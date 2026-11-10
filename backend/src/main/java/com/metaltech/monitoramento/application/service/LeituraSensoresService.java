package com.metaltech.monitoramento.application.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.metaltech.monitoramento.domain.model.AlertaNivel;
import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoAlerta;
import com.metaltech.monitoramento.domain.model.TipoPeca;
import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeituraSensoresService {

    /** Alertas ligados ao nível do reservatório (os únicos que são deduplicados e resolvidos automaticamente). */
    static final Set<TipoAlerta> ALERTAS_RESERVATORIO = EnumSet.of(
            TipoAlerta.RESERVATORIO_BAIXO,
            TipoAlerta.RESERVATORIO_ALTO,
            TipoAlerta.RESERVATORIO_CRITICO);

    private final LeituraSensoresRepository leituraRepository;
    private final AlertaNivelRepository alertaRepository;

    /**
     * Registra uma leitura da esteira aplicando as regras de negócio:
     * 1. o status do reservatório é recalculado a partir do percentual (fonte única da regra 20/80/95);
     * 2. alertas de reservatório que não refletem mais a condição atual são resolvidos automaticamente;
     * 3. um novo alerta de reservatório só é aberto se não houver outro ativo do mesmo tipo no dispositivo;
     * 4. conflito de sensores (AMBAS) sempre gera alerta, pois cada ocorrência é uma peça mal classificada.
     */
    @Transactional
    public LeituraSensores salvarLeitura(LeituraSensores leitura) {
        aplicarStatusReservatorio(leitura);

        TipoAlerta alertaReservatorio = alertaParaStatus(leitura.getStatusReservatorio());
        resolverAlertasSuperados(leitura.getDispositivoId(), alertaReservatorio);

        if (alertaReservatorio != null && !existeAlertaAtivo(alertaReservatorio, leitura.getDispositivoId())) {
            leitura.adicionarAlerta(criarAlerta(leitura, alertaReservatorio, mensagemReservatorio(leitura)));
        }

        if (leitura.getTipoPeca() == TipoPeca.AMBAS) {
            leitura.adicionarAlerta(criarAlerta(leitura, TipoAlerta.CONTAGEM_ANOMALA,
                    "Conflito de sensores: indutivo e capacitivo detectaram a mesma peça"));
        }

        // Alertas são persistidos em cascata junto com a leitura
        return leituraRepository.save(leitura);
    }

    @Transactional(readOnly = true)
    public Optional<LeituraSensores> buscarPorId(Long id) {
        return leituraRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<LeituraSensores> buscarPorPeriodo(Instant inicio, Instant fim) {
        return leituraRepository.findByTimestampBetween(inicio, fim);
    }

    @Transactional(readOnly = true)
    public List<LeituraSensores> buscarPorTipoPeca(TipoPeca tipo) {
        return leituraRepository.findByTipoPeca(tipo);
    }

    @Transactional(readOnly = true)
    public List<LeituraSensores> buscarPorStatusReservatorio(StatusReservatorio status) {
        return leituraRepository.findByStatusReservatorio(status);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> listarTodas(Pageable pageable) {
        return leituraRepository.findAllByOrderByTimestampDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> findByTimestampAndTipoPeca(Instant inicio, Instant fim, TipoPeca tipo, Pageable pageable) {
        return leituraRepository.findByTimestampAndTipoPeca(inicio, fim, tipo, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> findByTimestampAndStatusReservatorio(Instant inicio, Instant fim, StatusReservatorio status, Pageable pageable) {
        return leituraRepository.findByTimestampAndStatusReservatorio(inicio, fim, status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> findByTimestampBetween(Instant inicio, Instant fim, Pageable pageable) {
        return leituraRepository.findByTimestampBetween(inicio, fim, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> findByTipoPeca(TipoPeca tipo, Pageable pageable) {
        return leituraRepository.findByTipoPeca(tipo, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> findByStatusReservatorio(StatusReservatorio status, Pageable pageable) {
        return leituraRepository.findByStatusReservatorio(status, pageable);
    }

    @Transactional
    public void deletarPorId(Long id) {
        leituraRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<AlertaNivel> buscarAlertasNaoResolvidos() {
        return alertaRepository.findByResolvidoFalse();
    }

    @Transactional
    public void resolverAlerta(Long alertaId) {
        AlertaNivel alerta = alertaRepository.findById(alertaId)
                .orElseThrow(() -> new EntityNotFoundException("Alerta não encontrado: " + alertaId));
        alerta.marcarComoResolvido();
    }

    private void aplicarStatusReservatorio(LeituraSensores leitura) {
        StatusReservatorio calculado = StatusReservatorio.aPartirDoPercentual(leitura.getNivelReservatorioPercentual());
        StatusReservatorio informado = leitura.getStatusReservatorio();
        if (informado != null && informado != calculado) {
            log.warn("Status informado pelo dispositivo {} ({}) diverge do calculado para {}% ({}); usando o calculado",
                    leitura.getDispositivoId(), informado, leitura.getNivelReservatorioPercentual(), calculado);
        }
        leitura.setStatusReservatorio(calculado);
    }

    private TipoAlerta alertaParaStatus(StatusReservatorio status) {
        return switch (status) {
            case ALERTA_BAIXO -> TipoAlerta.RESERVATORIO_BAIXO;
            case ALERTA_ALTO -> TipoAlerta.RESERVATORIO_ALTO;
            case CRITICO -> TipoAlerta.RESERVATORIO_CRITICO;
            case NORMAL -> null;
        };
    }

    /** Resolve os alertas de reservatório ativos cuja condição não se aplica mais à leitura atual. */
    private void resolverAlertasSuperados(String dispositivoId, TipoAlerta alertaAtual) {
        Set<TipoAlerta> superados = EnumSet.copyOf(ALERTAS_RESERVATORIO);
        if (alertaAtual != null) {
            superados.remove(alertaAtual);
        }
        List<AlertaNivel> abertos = alertaRepository
                .findByTipoAlertaInAndResolvidoFalseAndLeitura_DispositivoId(superados, dispositivoId);
        abertos.forEach(AlertaNivel::marcarComoResolvido);
    }

    private boolean existeAlertaAtivo(TipoAlerta tipo, String dispositivoId) {
        return alertaRepository.existsByTipoAlertaAndResolvidoFalseAndLeitura_DispositivoId(tipo, dispositivoId);
    }

    private String mensagemReservatorio(LeituraSensores leitura) {
        String nivel = String.format(java.util.Locale.ROOT, "%.2f%%", leitura.getNivelReservatorioPercentual());
        return switch (leitura.getStatusReservatorio()) {
            case ALERTA_BAIXO -> "Nível do reservatório baixo: " + nivel;
            case ALERTA_ALTO -> "Nível do reservatório alto: " + nivel + " - programar esvaziamento";
            case CRITICO -> "Nível do reservatório crítico: " + nivel + " - risco de transbordo, esvaziar imediatamente";
            case NORMAL -> "Nível do reservatório normal: " + nivel;
        };
    }

    private AlertaNivel criarAlerta(LeituraSensores leitura, TipoAlerta tipo, String mensagem) {
        // O alerta herda o instante da leitura para manter o histórico coerente com o dado de origem
        Instant instante = leitura.getTimestamp() != null ? leitura.getTimestamp() : Instant.now();
        return new AlertaNivel(leitura, instante, tipo, mensagem, false);
    }
}
