package ru.itmo.isitmolab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itmo.isitmolab.model.VehicleImportOperation;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleImportHistoryItemDto {
    private Long id;
    private boolean success;
    private String username;
    private Integer importedCount;
    private LocalDateTime creationTime;

    public static VehicleImportHistoryItemDto toDto(VehicleImportOperation op) {

        boolean success = Boolean.TRUE.equals(op.getStatus());

        return VehicleImportHistoryItemDto.builder()
                .id(op.getId())
                .success(success)
                .importedCount(success ? op.getImportedCount() : null)
                .creationTime(op.getCreationTime())
                .build();
    }
}
