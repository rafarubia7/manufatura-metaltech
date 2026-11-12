package com.metaltech.monitoramento.domain.sensor;

import com.metaltech.monitoramento.domain.model.TipoPeca;

/** Sensor de material: quando acionado, indica o tipo de peça que ele detecta. */
public abstract class Sensor {

    private final boolean acionado;
    private final Double intensidade;

    protected Sensor(boolean acionado, Double intensidade) {
        this.acionado = acionado;
        this.intensidade = intensidade;
    }

    public boolean isAcionado() {
        return acionado;
    }

    public Double getIntensidade() {
        return intensidade;
    }

    /** Tipo de peça que o acionamento deste sensor indica. Cada subclasse define o seu. */
    public abstract TipoPeca tipoIndicado();
}
