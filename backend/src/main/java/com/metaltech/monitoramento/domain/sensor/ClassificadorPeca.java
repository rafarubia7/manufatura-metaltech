package com.metaltech.monitoramento.domain.sensor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.metaltech.monitoramento.domain.model.TipoPeca;

/**
 * Combina os sensores de material para classificar a peça. Não conhece as subclasses.
 *
 * <p>O capacitivo também reage a metal (na bancada ele fica sobreposto ao indutivo), então
 * indutivo + capacitivo juntos é uma peça metálica, não um conflito: o indutivo tem prioridade.
 */
public final class ClassificadorPeca {

    private ClassificadorPeca() {
    }

    public static TipoPeca classificar(List<? extends Sensor> sensores) {
        Set<TipoPeca> indicados = sensores.stream()
                .filter(Sensor::isAcionado)
                .map(Sensor::tipoIndicado)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TipoPeca.class)));

        if (indicados.contains(TipoPeca.METALICA)) {
            return TipoPeca.METALICA;
        }
        if (indicados.isEmpty()) {
            return TipoPeca.DESCONHECIDA;
        }
        return indicados.iterator().next();
    }
}
