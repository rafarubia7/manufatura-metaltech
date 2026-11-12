package com.metaltech.monitoramento.domain.sensor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.metaltech.monitoramento.domain.model.TipoPeca;

/** Combina os sensores de material para classificar a peça. Não conhece as subclasses. */
public final class ClassificadorPeca {

    private ClassificadorPeca() {
    }

    public static TipoPeca classificar(List<? extends Sensor> sensores) {
        Set<TipoPeca> indicados = sensores.stream()
                .filter(Sensor::isAcionado)
                .map(Sensor::tipoIndicado)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TipoPeca.class)));

        if (indicados.size() > 1) {
            return TipoPeca.AMBAS;
        }
        if (indicados.isEmpty()) {
            return TipoPeca.DESCONHECIDA;
        }
        return indicados.iterator().next();
    }
}
