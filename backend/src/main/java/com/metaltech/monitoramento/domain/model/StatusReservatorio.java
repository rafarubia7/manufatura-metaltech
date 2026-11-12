package com.metaltech.monitoramento.domain.model;

public enum StatusReservatorio {
    NORMAL,
    ALERTA_BAIXO,
    ALERTA_ALTO,
    CRITICO;

    public static final double LIMITE_BAIXO = 20.0;
    public static final double LIMITE_ALTO = 80.0;
    public static final double LIMITE_CRITICO = 95.0;

    // A faixa crítica é avaliada primeiro para não ficar presa em ALERTA_ALTO.
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
