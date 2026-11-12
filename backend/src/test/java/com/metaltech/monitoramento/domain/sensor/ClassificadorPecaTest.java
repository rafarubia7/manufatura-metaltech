package com.metaltech.monitoramento.domain.sensor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.metaltech.monitoramento.domain.model.TipoPeca;

class ClassificadorPecaTest {

    private TipoPeca classificar(Boolean metal, Boolean naoMetal) {
        return ClassificadorPeca.classificar(List.of(
                new SensorIndutivo(metal, 0.5), new SensorCapacitivo(naoMetal, 0.5)));
    }

    @Test
    void deveClassificarMetalica() {
        assertThat(classificar(true, false)).isEqualTo(TipoPeca.METALICA);
    }

    @Test
    void deveClassificarNaoMetalica() {
        assertThat(classificar(false, true)).isEqualTo(TipoPeca.NAO_METALICA);
    }

    @Test
    void deveClassificarAmbasQuandoOsDoisSensoresDisparam() {
        assertThat(classificar(true, true)).isEqualTo(TipoPeca.AMBAS);
    }

    @Test
    void deveClassificarDesconhecidaQuandoNenhumDispara() {
        assertThat(classificar(false, false)).isEqualTo(TipoPeca.DESCONHECIDA);
    }

    @Test
    void deveTratarFlagNuloComoNaoAcionado() {
        assertThat(classificar(null, null)).isEqualTo(TipoPeca.DESCONHECIDA);
    }

    @Test
    void cadaSubclasseIndicaSeuProprioTipo() {
        assertThat(new SensorIndutivo(true, null).tipoIndicado()).isEqualTo(TipoPeca.METALICA);
        assertThat(new SensorCapacitivo(true, null).tipoIndicado()).isEqualTo(TipoPeca.NAO_METALICA);
    }
}
