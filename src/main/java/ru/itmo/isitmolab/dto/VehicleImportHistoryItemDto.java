package ru.itmo.isitmolab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
