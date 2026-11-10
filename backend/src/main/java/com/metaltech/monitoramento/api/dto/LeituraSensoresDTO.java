package com.metaltech.monitoramento.api.dto;

import java.time.Instant;
import java.util.Objects;

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
public class LeituraSensoresDTO {

    @NotNull
    private Instant timestamp;

    @NotNull
    private SensorDistanciaDTO sensorDistancia;

    @NotNull
    private SensorIndutivoDTO sensorIndutivo;

    @NotNull
    private SensorCapacitivoDTO sensorCapacitivo;

    @NotNull
    private PecaDTO peca;

    @NotNull
    private NivelReservatorioDTO nivelReservatorio;

    @NotNull
    private String dispositivoId;

    private String versaoFirmware;

    // Validação cruzada de campos (se necessário)
    public boolean isValid() {
        return Objects.nonNull(sensorDistancia) 
            && Objects.nonNull(sensorIndutivo) 
            && Objects.nonNull(sensorCapacitivo) 
            && Objects.nonNull(peca) 
            && Objects.nonNull(nivelReservatorio);
    }
}