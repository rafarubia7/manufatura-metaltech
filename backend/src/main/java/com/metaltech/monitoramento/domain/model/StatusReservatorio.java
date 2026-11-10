package com.metaltech.monitoramento.domain.model;

/**
 * Faixas operacionais do reservatório de peças triadas.
 * NORMAL entre 20% e 80%, ALERTA_BAIXO abaixo de 20%,
 * ALERTA_ALTO acima de 80% e CRITICO a partir de 95%.
 */
public enum StatusReservatorio {
    NORMAL,
    ALERTA_BAIXO,
    ALERTA_ALTO,
    CRITICO;

    public static final double LIMITE_BAIXO = 20.0;
    public static final double LIMITE_ALTO = 80.0;
    public static final double LIMITE_CRITICO = 95.0;

    /**
     * Classifica o nível do reservatório. A faixa crítica é avaliada primeiro
     * para que valores acima de 95% não fiquem presos em ALERTA_ALTO.
     */
    public static StatusReservatorio aPartirDoPercentual(double percentual) {
        if (percentual >= LIMITE_CRITICO) {
            return CRITICO;
        }
        if (percentual > LIMITE_ALTO) {
            return ALERTA_ALTO;
        }
        if (percentual < LIMITE_BAIXO) {
            return ALERTA_BAIXO;
        }
        return NORMAL;
    }
}
