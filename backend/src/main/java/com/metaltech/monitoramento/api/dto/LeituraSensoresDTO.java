package com.metaltech.monitoramento.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeituraSensoresDTO {

    @NotNull
    @JsonProperty("timestamp")
    private Instant dataHora;

    @NotNull
    @Valid
    private SensorDistanciaDTO sensorDistancia;

    @NotNull
    @Valid
    private SensorIndutivoDTO sensorIndutivo;

    @NotNull
    @Valid
    private SensorCapacitivoDTO sensorCapacitivo;

    @NotNull
    @Valid
    private PecaDTO peca;

    @NotNull
    @Valid
    private NivelReservatorioDTO nivelReservatorio;

    @NotNull
    private String dispositivoId;

    private String versaoFirmware;
}
