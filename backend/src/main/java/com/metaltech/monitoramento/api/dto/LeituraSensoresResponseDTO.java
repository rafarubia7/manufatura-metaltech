package com.metaltech.monitoramento.api.dto;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeituraSensoresResponseDTO {

    private Long id;
    private Instant timestamp;
    private SensorDistanciaDTO sensorDistancia;
    private SensorIndutivoDTO sensorIndutivo;
    private SensorCapacitivoDTO sensorCapacitivo;
    private PecaDTO peca;
    private NivelReservatorioDTO nivelReservatorio;
    private String dispositivoId;
    private String versaoFirmware;
    private List<AlertaNivelResponseDTO> alertas;

    }
