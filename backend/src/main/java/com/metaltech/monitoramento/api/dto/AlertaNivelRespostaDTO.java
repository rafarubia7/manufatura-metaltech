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
public class AlertaNivelRespostaDTO {

    private Long id;
    private Long leituraId;
    private String dispositivoId;
    private Instant dataHoraAlerta;
    private String tipoAlerta;
    private String mensagem;
    private Boolean resolvido;
}
