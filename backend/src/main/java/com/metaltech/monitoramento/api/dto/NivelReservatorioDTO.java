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
public class NivelReservatorioDTO {

    @NotNull
    @PositiveOrZero
    private Double nivelAtual;

    @NotNull
    @PositiveOrZero
    private Double capacidadeMaxima;

    @NotNull
    @PositiveOrZero
    private Double percentual;

    @NotNull
    private String status;
}