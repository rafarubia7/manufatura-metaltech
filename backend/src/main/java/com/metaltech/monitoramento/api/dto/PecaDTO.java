package com.metaltech.monitoramento.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PecaDTO {

    @NotNull
    private String id;

    @NotNull
    @Pattern(regexp = "METALICA|NAO_METALICA|DESCONHECIDA|AMBAS")
    private String tipo;

    @NotNull
    @JsonProperty("timestampDetectado")
    private Instant dataHoraDeteccao;
}
