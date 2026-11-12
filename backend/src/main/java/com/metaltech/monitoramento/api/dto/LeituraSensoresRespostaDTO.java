package com.metaltech.monitoramento.api.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"id", "timestamp"})
public class LeituraSensoresRespostaDTO {

    private Long id;

    @JsonProperty("timestamp")
    private Instant dataHora;

    private SensorDistanciaDTO sensorDistancia;
    private SensorIndutivoDTO sensorIndutivo;
    private SensorCapacitivoDTO sensorCapacitivo;
    private PecaDTO peca;
    private NivelReservatorioDTO nivelReservatorio;
    private String dispositivoId;
    private String versaoFirmware;
    private List<AlertaNivelRespostaDTO> alertas;
}
