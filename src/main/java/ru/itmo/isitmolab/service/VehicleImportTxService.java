package ru.itmo.isitmolab.service;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import ru.itmo.isitmolab.dao.VehicleDao;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.dto.VehicleImportItemDto;
import ru.itmo.isitmolab.model.Coordinates;
import ru.itmo.isitmolab.model.Vehicle;
import ru.itmo.isitmolab.storage.MinioStorageService;
import ru.itmo.isitmolab.ws.VehicleWsService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class VehicleImportTxService {

    @Resource
    private TransactionSynchronizationRegistry txRegistry;

    @Inject
    private MinioStorageService minioStorage;

    @Inject
    private VehicleImportOperationLogger opLogger;

    @Inject
    private VehicleService vehicleService;

    @Inject
    private VehicleDao vehicleDao;

    @Inject
    private VehicleWsService wsHub;

    /**
     * Фаза 1: вставка vehicles в БД (внутри JTA)
     * Фаза 2: сохранение файла в MinIO в beforeCompletion()
     *
     * Условие: при ОШИБКЕ файл НЕ должен остаться в MinIO.
     */
    @Transactional
    public void importVehiclesTx(Long opId,
                                 List<VehicleImportItemDto> items,
                                 byte[] fileBytes,
                                 String finalKey,
                                 String safeName,
                                 String contentType,
                                 long size) {

        final AtomicInteger importedCount = new AtomicInteger(0);

        txRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // === ФАЗА 2 (MinIO) ===
                // Если MinIO упадёт -> бросаем исключение -> JTA rollback -> vehicles не коммитятся
                minioStorage.putObject(
                        finalKey,
                        fileBytes,
                        contentType,
                        Map.of("original-name", safeName)
                );
            }

            @Override
            public void afterCompletion(int status) {
                // afterCompletion не должен бросать
                try {
                    if (status == Status.STATUS_COMMITTED) {
                        // успех: и БД, и MinIO
                        safeLogSuccess(opId, importedCount.get(), finalKey, safeName, contentType, size);
                        safeBroadcastRefresh();
                        return;
                    }

                    // rollback: файл НЕ должен оставаться в MinIO
                    minioStorage.removeObjectQuietly(finalKey);

                    // и в логе не должно быть ссылки на файл
                    safeLogFailure(opId, importedCount.get(), null, safeName, contentType, size);

                } catch (Throwable ignored) {
                    // no-op
                }
            }
        });

        // === ФАЗА 1 (DB) ===
        for (VehicleImportItemDto item : items) {
            VehicleDto dto = VehicleImportItemDto.toEntity(item);

            vehicleService.checkUniqueVehicleName(dto.getName(), null);

            Coordinates coords = vehicleService.resolveCoordinatesForDto(dto);
            Vehicle v = VehicleDto.toEntity(dto, null);
            v.setCoordinates(coords);

            vehicleDao.save(v);
            importedCount.incrementAndGet();
        }
    }

    private void safeLogSuccess(Long opId, int importedCount, String finalKey, String fileName, String ct, long size) {
        try {
            opLogger.markSuccess(opId, importedCount, finalKey, fileName, ct, size);
        } catch (RuntimeException ignored) {}
    }

    private void safeLogFailure(Long opId, int importedCount, String keyToStore, String fileName, String ct, long size) {
        try {
            opLogger.markFailure(opId, importedCount, keyToStore, fileName, ct, size);
        } catch (RuntimeException ignored) {}
    }

    private void safeBroadcastRefresh() {
        try {
            wsHub.broadcastText("refresh");
        } catch (RuntimeException ignored) {}
    }
}