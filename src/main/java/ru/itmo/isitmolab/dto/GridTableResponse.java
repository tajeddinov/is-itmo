package ru.itmo.isitmolab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GridTableResponse<T> {
    public List<T> rows;
    public Integer lastRow;
}
