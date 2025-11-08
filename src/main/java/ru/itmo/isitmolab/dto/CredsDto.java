package ru.itmo.isitmolab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CredsDto {
    @NotBlank
    private String login;
    @NotBlank
    private String password;
}
