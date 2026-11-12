package com.metaltech.monitoramento.domain.sensor;

import com.metaltech.monitoramento.domain.model.TipoPeca;

/** Detecta apenas materiais condutores (metal). */
public class SensorIndutivo extends Sensor {

    public SensorIndutivo(Boolean acionado, Double intensidade) {
        super(Boolean.TRUE.equals(acionado), intensidade);
    }

    @Override
    public TipoPeca tipoIndicado() {
        return TipoPeca.METALICA;
    }
}
