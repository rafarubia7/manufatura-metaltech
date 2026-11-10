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
public class SensorCapacitivoDTO {

    @NotNull
    private Boolean detectouNaoMetal;

    @PositiveOrZero
    private Double intensidade;

    @NotNull
    private String status;
}
