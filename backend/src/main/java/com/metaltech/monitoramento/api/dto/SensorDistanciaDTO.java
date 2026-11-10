package com.metaltech.monitoramento.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SensorDistanciaDTO {

    @NotNull
    @PositiveOrZero
    private Double valor;

    @NotNull
    private String unidade;

    @NotNull
    private String status;
}
