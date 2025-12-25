package ru.itmo.isitmolab.service;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.validation.ConstraintViolation;
import ru.itmo.isitmolab.dao.VehicleDao;
import ru.itmo.isitmolab.dao.VehicleImportOperationDao;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.dto.VehicleImportErrors;
import ru.itmo.isitmolab.dto.VehicleImportHistoryItemDto;
import ru.itmo.isitmolab.dto.VehicleImportItemDto;
import ru.itmo.isitmolab.exception.VehicleValidationException;
import ru.itmo.isitmolab.model.Coordinates;
import ru.itmo.isitmolab.model.Vehicle;
import ru.itmo.isitmolab.model.VehicleImportOperation;
import ru.itmo.isitmolab.storage.MinioStorageService;
import ru.itmo.isitmolab.util.BeanValidation;
import ru.itmo.isitmolab.ws.VehicleWsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ApplicationScoped
public class VehicleImportService {

    private static final String MINIO_IMPORT_PREFIX = "imports";

    @Resource
    private TransactionSynchronizationRegistry txRegistry;

    @Inject
    private MinioStorageService minioStorage;

    @Inject
    private VehicleImportOperationDao importOperationDao;
    @Inject
    private VehicleImportService self;
    @Inject
    private VehicleService vehicleService;
    @Inject
    private VehicleDao dao;
    @Inject
    private VehicleWsService wsHub;


    @Transactional
    public void importVehicles(List<VehicleImportItemDto> items, byte[] rawFile, String originalFileName, String contentType) {

        AtomicInteger importedCount = new AtomicInteger(0);

        String opId = UUID.randomUUID().toString();
        String ext = ".json";
        String safeName = (originalFileName == null || originalFileName.isBlank()) ? ("import-" + opId + ext) : originalFileName;

        String tempKey = MINIO_IMPORT_PREFIX + "/tmp/" + opId + ext;
        String finalKey = MINIO_IMPORT_PREFIX + "/" + opId + ext;
        long size = rawFile == null ? 0 : rawFile.length;

        // Phase 1: stage file in MinIO (outside DB, but rollback-safe via tx synchronization)
        minioStorage.putObject(
                tempKey,
                rawFile == null ? new byte[0] : rawFile,
                contentType,
                Map.of("original-name", safeName)
        );

        // Phase 2: register two-phase actions tied to DB transaction
        txRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // "commit" file: promote temp object to final key; if fails -> rollback DB tx
                minioStorage.copyObject(tempKey, finalKey);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    minioStorage.removeObjectQuietly(tempKey);
                    self.logImportOperation(true, importedCount.get(), finalKey, safeName, contentType, size);
                    wsHub.broadcastText("refresh");
                } else {
                    minioStorage.removeObjectQuietly(tempKey);
                    minioStorage.removeObjectQuietly(finalKey);
                    self.logImportOperation(false, importedCount.get(), null, null, null, null);
                }
            }
        });

        try {
            // VALIDATION
            List<VehicleImportErrors.RowError> validationErrors = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                VehicleImportItemDto item = items.get(i);
                Set<ConstraintViolation<VehicleImportItemDto>> violations = BeanValidation.validate(item);

                for (ConstraintViolation<VehicleImportItemDto> violation : violations) {
                    validationErrors.add(new VehicleImportErrors.RowError(
                            i + 1,
                            violation.getPropertyPath().toString(),
                            violation.getMessage()
                    ));
                }
            }

            if (!validationErrors.isEmpty()) {
                throw new VehicleValidationException("Validation failed", validationErrors);
            }

            // IMPORT
            for (VehicleImportItemDto item : items) {
                VehicleDto dto = VehicleImportItemDto.toEntity(item);

                // БИЗНЕС-ОГРАНИЧЕНИЕ: уникальность имени
                vehicleService.checkUniqueVehicleName(dto.getName(), null);

                Coordinates coords = vehicleService.resolveCoordinatesForDto(dto);
                Vehicle v = VehicleDto.toEntity(dto, null);
                v.setCoordinates(coords);
                dao.save(v);

                importedCount.incrementAndGet();
            }

        } catch (Exception e) {
            throw e;
        }
    }


    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void logImportOperation(boolean success,
                                  Integer importedCount,
                                  String fileObjectKey,
                                  String fileName,
                                  String fileContentType,
                                  Long fileSize) {
        VehicleImportOperation op = new VehicleImportOperation();
        op.setStatus(success);
        op.setImportedCount(importedCount);
        op.setFileObjectKey(fileObjectKey);
        op.setFileName(fileName);
        op.setFileContentType(fileContentType);
        op.setFileSize(fileSize);
        importOperationDao.save(op);
    }

    public VehicleImportOperation getOperationOrThrow(Long id) {
        return importOperationDao.findById(id)
                .orElseThrow(() -> new jakarta.ws.rs.WebApplicationException("Import operation not found: " + id,
                        jakarta.ws.rs.core.Response.Status.NOT_FOUND));
    }

    public List<VehicleImportHistoryItemDto> getHistoryForAdmin(int limit) {
        return importOperationDao.findLastForAdmin(limit)
                .stream()
                .map(VehicleImportHistoryItemDto::toDto)
                .collect(Collectors.toList());
    }

}
