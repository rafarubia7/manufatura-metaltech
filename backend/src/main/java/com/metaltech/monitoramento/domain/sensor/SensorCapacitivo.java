package com.metaltech.monitoramento.domain.sensor;

import com.metaltech.monitoramento.domain.model.TipoPeca;

/** Detecta qualquer material; aqui indica peça não metálica (plástico, madeira etc.). */
public class SensorCapacitivo extends Sensor {

    public SensorCapacitivo(Boolean acionado, Double intensidade) {
        super(Boolean.TRUE.equals(acionado), intensidade);
    }

    @Override
    public TipoPeca tipoIndicado() {
        return TipoPeca.NAO_METALICA;
    }
}
