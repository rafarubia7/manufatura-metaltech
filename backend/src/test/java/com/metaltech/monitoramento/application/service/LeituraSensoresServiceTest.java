package com.metaltech.monitoramento.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.metaltech.monitoramento.domain.model.AlertaNivel;
import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoAlerta;
import com.metaltech.monitoramento.domain.model.TipoPeca;
import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeituraSensoresServiceTest {

    @Mock
    private LeituraSensoresRepository leituraRepository;

    @Mock
    private AlertaNivelRepository alertaRepository;

    @InjectMocks
    private LeituraSensoresService leituraService;

    private Instant timestamp;

    @BeforeEach
    void setUp() {
        timestamp = Instant.parse("2026-11-03T10:00:00Z");
        when(leituraRepository.save(any(LeituraSensores.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alertaRepository.findByTipoAlertaInAndResolvidoFalseAndLeitura_DispositivoId(anyCollection(), anyString()))
                .thenReturn(List.of());
    }

    private LeituraSensores leitura(TipoPeca tipo, double percentual, StatusReservatorio statusInformado) {
        return new LeituraSensores(null, timestamp, 12.5, true, 0.85, tipo == TipoPeca.AMBAS, 0.12,
                tipo, percentual, statusInformado, "ESP32-001", "1.0.0", Collections.emptyList());
    }

    @Test
    void naoDeveGerarAlertaParaLeituraNormal() {
        LeituraSensores salva = leituraService.salvarLeitura(leitura(TipoPeca.METALICA, 75.5, StatusReservatorio.NORMAL));

        assertThat(salva.getStatusReservatorio()).isEqualTo(StatusReservatorio.NORMAL);
        assertThat(salva.getAlertas()).isEmpty();
        verify(leituraRepository).save(salva);
    }

    @Test
    void deveRecalcularStatusQuandoDispositivoInformaStatusIncorreto() {
        LeituraSensores salva = leituraService.salvarLeitura(leitura(TipoPeca.METALICA, 97.0, StatusReservatorio.NORMAL));

        assertThat(salva.getStatusReservatorio()).isEqualTo(StatusReservatorio.CRITICO);
    }

    @Test
    void deveGerarAlertaCriticoVinculadoALeituraComTimestampDaLeitura() {
        LeituraSensores salva = leituraService.salvarLeitura(leitura(TipoPeca.METALICA, 96.0, null));

        assertThat(salva.getAlertas()).hasSize(1);
        AlertaNivel alerta = salva.getAlertas().get(0);
        assertThat(alerta.getTipoAlerta()).isEqualTo(TipoAlerta.RESERVATORIO_CRITICO);
        assertThat(alerta.getLeitura()).isSameAs(salva);
        assertThat(alerta.getTimestampAlertado()).isEqualTo(timestamp);
        assertThat(alerta.getResolvido()).isFalse();
        // Persistência acontece em cascata pela leitura, nunca isoladamente
        verify(alertaRepository, never()).save(any(AlertaNivel.class));
    }

    @Test
    void naoDeveDuplicarAlertaDeReservatorioJaAtivo() {
        when(alertaRepository.existsByTipoAlertaAndResolvidoFalseAndLeitura_DispositivoId(
                TipoAlerta.RESERVATORIO_ALTO, "ESP32-001")).thenReturn(true);

        LeituraSensores salva = leituraService.salvarLeitura(leitura(TipoPeca.METALICA, 85.0, StatusReservatorio.ALERTA_ALTO));

        assertThat(salva.getAlertas()).isEmpty();
    }

    @Test
    void deveResolverAlertasDeReservatorioQuandoNivelVoltaAoNormal() {
        AlertaNivel aberto = new AlertaNivel(leitura(TipoPeca.METALICA, 85.0, null), timestamp,
                TipoAlerta.RESERVATORIO_ALTO, "alto", false);
        when(alertaRepository.findByTipoAlertaInAndResolvidoFalseAndLeitura_DispositivoId(anyCollection(), eq("ESP32-001")))
                .thenReturn(List.of(aberto));

        leituraService.salvarLeitura(leitura(TipoPeca.METALICA, 30.0, StatusReservatorio.NORMAL));

        assertThat(aberto.getResolvido()).isTrue();
    }

    @Test
    void deveGerarAlertaDeConflitoParaPecaAmbas() {
        LeituraSensores salva = leituraService.salvarLeitura(leitura(TipoPeca.AMBAS, 50.0, StatusReservatorio.NORMAL));

        assertThat(salva.getAlertas())
                .extracting(AlertaNivel::getTipoAlerta)
                .containsExactly(TipoAlerta.CONTAGEM_ANOMALA);
    }

    @Test
    void deveBuscarPorPeriodo() {
        LeituraSensores l = leitura(TipoPeca.METALICA, 50.0, null);
        when(leituraRepository.findByTimestampBetween(any(Instant.class), any(Instant.class))).thenReturn(List.of(l));

        assertThat(leituraService.buscarPorPeriodo(timestamp, timestamp)).hasSize(1);
    }

    @Test
    void deveListarTodasComPaginacao() {
        Page<LeituraSensores> page = new PageImpl<>(List.of(leitura(TipoPeca.METALICA, 50.0, null)));
        when(leituraRepository.findAllByOrderByTimestampDesc(any(Pageable.class))).thenReturn(page);

        assertThat(leituraService.listarTodas(Pageable.unpaged()).getContent()).hasSize(1);
    }

    @Test
    void deveResolverAlertaExistente() {
        AlertaNivel alerta = new AlertaNivel(null, timestamp, TipoAlerta.RESERVATORIO_ALTO, "alto", false);
        when(alertaRepository.findById(1L)).thenReturn(Optional.of(alerta));

        leituraService.resolverAlerta(1L);

        assertThat(alerta.getResolvido()).isTrue();
    }

    @Test
    void deveLancarExcecaoAoResolverAlertaInexistente() {
        when(alertaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leituraService.resolverAlerta(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
