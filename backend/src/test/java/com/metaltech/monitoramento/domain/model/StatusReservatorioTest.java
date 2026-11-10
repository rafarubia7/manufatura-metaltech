package com.metaltech.monitoramento.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatusReservatorioTest {

    @ParameterizedTest(name = "{0}% -> {1}")
    @CsvSource({
        "0.0, ALERTA_BAIXO",
        "19.99, ALERTA_BAIXO",
        "20.0, NORMAL",
        "50.0, NORMAL",
        "80.0, NORMAL",
        "80.01, ALERTA_ALTO",
        "94.99, ALERTA_ALTO",
        "95.0, CRITICO",
        "100.0, CRITICO"
    })
    void deveClassificarNivelNasFronteiras(double percentual, StatusReservatorio esperado) {
        assertThat(StatusReservatorio.aPartirDoPercentual(percentual)).isEqualTo(esperado);
    }
}
