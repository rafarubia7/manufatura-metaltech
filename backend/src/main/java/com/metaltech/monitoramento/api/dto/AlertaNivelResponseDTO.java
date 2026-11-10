package com.metaltech.monitoramento.api.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertaNivelResponseDTO {

    private Long id;
    private Instant timestampAlertado;
    private String tipoAlerta;
    private String mensagem;
    private Boolean resolvido;
}