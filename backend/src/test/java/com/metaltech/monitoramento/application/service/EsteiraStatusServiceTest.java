package com.metaltech.monitoramento.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoAlerta;
import com.metaltech.monitoramento.domain.model.TipoPeca;
import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class EsteiraStatusServiceTest {

    @Mock
    private LeituraSensoresRepository leituraRepository;

    @Mock
    private AlertaNivelRepository alertaRepository;

    @InjectMocks
    private EsteiraStatusService service;

    @Test
    void deveMontarStatusComUltimaLeituraEContagens() {
        Instant dataHora = Instant.parse("2026-11-03T10:00:00Z");
        LeituraSensores ultima = new LeituraSensores(1L, dataHora, 9.5, true, 0.85, false, 0.12,
                TipoPeca.METALICA, 82.5, StatusReservatorio.ALERTA_ALTO, "ESP32-001", "1.0.0",
                Collections.emptyList());
        when(leituraRepository.findFirstByOrderByDataHoraDescIdDesc()).thenReturn(Optional.of(ultima));
        when(leituraRepository.countByTipoPeca(TipoPeca.METALICA)).thenReturn(6L);
        when(leituraRepository.countByTipoPeca(TipoPeca.NAO_METALICA)).thenReturn(3L);
        when(leituraRepository.countByTipoPeca(TipoPeca.DESCONHECIDA)).thenReturn(1L);
        when(leituraRepository.countByTipoPeca(TipoPeca.AMBAS)).thenReturn(0L);
        when(alertaRepository.contarNaoResolvidos()).thenReturn(2L);

        EsteiraStatus status = service.obterStatusAtual();

        assertThat(status.ultimaLeitura()).isEqualTo(dataHora);
        assertThat(status.nivelReservatorioPercentual()).isEqualTo(82.5);
        assertThat(status.statusReservatorio()).isEqualTo("ALERTA_ALTO");
        assertThat(status.totalMetalicas()).isEqualTo(6L);
        assertThat(status.totalNaoMetalicas()).isEqualTo(3L);
        assertThat(status.totalDesconhecidas()).isEqualTo(1L);
        assertThat(status.alertasAtivos()).isEqualTo(2L);
        assertThat(status.exibirAlerta()).isTrue(); // ALERTA_ALTO = perto da capacidade
    }

    private void prepararUltimaLeitura(double percentual, StatusReservatorio statusReservatorio) {
        LeituraSensores ultima = new LeituraSensores(1L, Instant.parse("2026-11-03T10:00:00Z"), 9.5, true, 0.85,
                false, 0.12, TipoPeca.METALICA, percentual, statusReservatorio, "ESP32-001", "1.0.0",
                Collections.emptyList());
        when(leituraRepository.findFirstByOrderByDataHoraDescIdDesc()).thenReturn(Optional.of(ultima));
    }

    @Test
    void deveExibirAlertaQuandoOEnchimentoEstaForaDoPadraoMesmoComNivelNormal() {
        prepararUltimaLeitura(45.0, StatusReservatorio.NORMAL);
        when(alertaRepository.listarTiposNaoResolvidos()).thenReturn(List.of(TipoAlerta.ENCHIMENTO_RAPIDO));

        EsteiraStatus status = service.obterStatusAtual();

        assertThat(status.tiposAlertaAtivos()).containsExactly("ENCHIMENTO_RAPIDO");
        assertThat(status.exibirAlerta()).isTrue();
    }

    @Test
    void naoDeveExibirAlertaComNivelNormalOuBaixoESemEnchimentoRapido() {
        prepararUltimaLeitura(10.0, StatusReservatorio.ALERTA_BAIXO);
        when(alertaRepository.listarTiposNaoResolvidos()).thenReturn(List.of(TipoAlerta.RESERVATORIO_BAIXO));

        EsteiraStatus status = service.obterStatusAtual();

        assertThat(status.tiposAlertaAtivos()).containsExactly("RESERVATORIO_BAIXO");
        assertThat(status.exibirAlerta()).isFalse();
    }

    @Test
    void deveLancarExcecaoQuandoNaoHaLeituras() {
        when(leituraRepository.findFirstByOrderByDataHoraDescIdDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obterStatusAtual()).isInstanceOf(EntityNotFoundException.class);
    }
}
