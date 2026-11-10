package com.metaltech.monitoramento.application.service;

import com.metaltech.monitoramento.domain.model.AlertaNivel;
import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoAlerta;
import com.metaltech.monitoramento.domain.model.TipoPeca;
import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import(LeituraSensoresService.class)
class LeituraSensoresServiceTest {

    @Autowired
    private LeituraSensoresService leituraService;

    @MockBean
    private LeituraSensoresRepository leituraRepository;

    @MockBean
    private AlertaNivelRepository alertaRepository;

    private LeituraSensores leitura;
    private Instant timestamp;

    @BeforeEach
    void setUp() {
        timestamp = Instant.now();
        leitura = new LeituraSensores(
            1L,
            timestamp,
            12.5,
            true,
            0.85,
            false,
            0.12,
            TipoPeca.METALICA,
            75.5,
            StatusReservatorio.NORMAL,
            "ESP32-001",
            "1.0.0",
            Collections.emptyList()
        );
    }

    @Test
    @Transactional
    void deveSalvarLeituraComAlertas() {
        // Configura repositório
        when(leituraRepository.save(any(LeituraSensores.class))).thenReturn(leitura);

        // Executa serviço
        LeituraSensores salva = leituraService.salvarLeitura(leitura);

        // Verifica que o alerta foi gerado (NORMAL não gera alerta)
        verify(leituraRepository).save(any(LeituraSensores.class));
    }

    @Test
    @Transactional
    void deveGerarAlertasReservatorioCritico() {
        // Leitura com reservatório crítico
        LeituraSensores leituraCritica = new LeituraSensores(
            2L,
            timestamp,
            12.5,
            true,
            0.85,
            false,
            0.12,
            TipoPeca.METALICA,
            96.0, // > 95 = CRITICO
            StatusReservatorio.CRITICO,
            "ESP32-001",
            "1.0.0",
            Collections.emptyList()
        );

        when(leituraRepository.save(any(LeituraSensores.class))).thenReturn(leituraCritica);

        LeituraSensores salva = leituraService.salvarLeitura(leituraCritica);

        // Verifica que o alerta crítico foi gerado
        verify(alertaRepository).save(any(AlertaNivel.class)); // Chamada para persistir alerta
    }

    @Test
    @Transactional
    void deveGerarAlertasConflitoSensores() {
        // Leitura com conflito de sensores
        LeituraSensores leituraConflito = new LeituraSensores(
            3L,
            timestamp,
            12.5,
            true,
            0.85,
            true,
            0.90,
            TipoPeca.AMBAS,
            75.5,
            StatusReservatorio.NORMAL,
            "ESP32-001",
            "1.0.0",
            Collections.emptyList()
        );

        when(leituraRepository.save(any(LeituraSensores.class))).thenReturn(leituraConflito);

        LeituraSensores salva = leituraService.salvarLeitura(leituraConflito);

        // Verifica que alerta de conflito foi gerado
        verify(alertaRepository).save(any(AlertaNivel.class));
    }

    @Test
    void deveBuscarPorPeriodo() {
        when(leituraRepository.findByTimestampBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(leitura));

        List<LeituraSensores> result = leituraService.buscarPorPeriodo(timestamp, timestamp);
        
        assertThat(result).hasSize(1);
        verify(leituraRepository).findByTimestampBetween(any(Instant.class), any(Instant.class));
    }

    @Test
    void deveBuscarPorTipoPeca() {
        when(leituraRepository.findByTipoPeca(TipoPeca.METALICA))
                .thenReturn(List.of(leitura));

        List<LeituraSensores> result = leituraService.buscarPorTipoPeca(TipoPeca.METALICA);
        
        assertThat(result).hasSize(1);
        verify(leituraRepository).findByTipoPeca(TipoPeca.METALICA);
    }

    @Test
    void deveListarTodasComPaginacao() {
        Page<LeituraSensores> page = new PageImpl<>(List.of(leitura));
        when(leituraRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(page);

        Page<LeituraSensores> result = leituraService.listarTodas(Pageable.unpaged());
        
        assertThat(result.getContent()).hasSize(1);
        verify(leituraRepository).findAllByOrderByTimestampDesc(any(Pageable.class));
    }

    @Test
    void deveBuscarAlertasNaoResolvidos() {
        List<AlertaNivel> alertas = List.of(new AlertaNivel(1L, leitura, Instant.now(), TipoAlerta.RESERVATORIO_ALTO, "Teste", false));
        when(alertaRepository.findByResolvidoFalse()).thenReturn(alertas);

        List<AlertaNivel> result = leituraService.buscarAlertasNaoResolvidos();
        
        assertThat(result).hasSize(1);
        verify(alertaRepository).findByResolvidoFalse();
    }

    @Test
    void deveResolverAlerta() {
        leituraService.resolverAlerta(1L);
        verify(alertaRepository).findById(1L);
    }
}