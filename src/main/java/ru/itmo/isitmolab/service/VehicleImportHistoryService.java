package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.itmo.isitmolab.dao.VehicleImportOperationDao;
import ru.itmo.isitmolab.dto.VehicleImportHistoryItemDto;
import ru.itmo.isitmolab.model.VehicleImportOperation;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class VehicleImportHistoryService {

    @Inject
    private VehicleImportOperationDao importOperationDao;

    public List<VehicleImportHistoryItemDto> getHistoryForAdmin(Long adminId, int limit) {
        return importOperationDao.findLastForAdmin(adminId, limit)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private VehicleImportHistoryItemDto toDto(VehicleImportOperation op) {
        // подставь из Admin реальное поле логина/имени
        String username = op.getAdmin().getLogin();

        boolean success = Boolean.TRUE.equals(op.getStatus());

        return VehicleImportHistoryItemDto.builder()
                .id(op.getId())
                .success(success)
                .username(username)
                .importedCount(success ? op.getImportedCount() : null)
                .creationTime(op.getCreationTime())
                .build();
    }

}
