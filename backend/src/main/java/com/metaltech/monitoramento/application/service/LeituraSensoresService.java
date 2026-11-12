package com.metaltech.monitoramento.application.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
import com.metaltech.monitoramento.domain.sensor.ClassificadorPeca;
import com.metaltech.monitoramento.domain.sensor.SensorCapacitivo;
import com.metaltech.monitoramento.domain.sensor.SensorIndutivo;
import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeituraSensoresService {

    static final Set<TipoAlerta> ALERTAS_RESERVATORIO = EnumSet.of(
            TipoAlerta.RESERVATORIO_BAIXO,
            TipoAlerta.RESERVATORIO_ALTO,
            TipoAlerta.RESERVATORIO_CRITICO);

    private final LeituraSensoresRepository leituraRepository;
    private final AlertaNivelRepository alertaRepository;

    /**
     * O status é sempre recalculado pelo percentual. Alertas de reservatório são únicos por tipo
     * e dispositivo e se resolvem quando a condição muda; cada conflito AMBAS gera um alerta.
     */
    @Transactional
    public LeituraSensores salvarLeitura(LeituraSensores leitura) {
        aplicarClassificacaoDaPeca(leitura);
        aplicarStatusReservatorio(leitura);

        TipoAlerta alertaReservatorio = alertaParaStatus(leitura.getStatusReservatorio());
        resolverAlertasSuperados(leitura.getDispositivoId(), alertaReservatorio);

        if (alertaReservatorio != null && !alertaRepository.existeAtivo(alertaReservatorio, leitura.getDispositivoId())) {
            leitura.adicionarAlerta(criarAlerta(leitura, alertaReservatorio, mensagemReservatorio(leitura)));
        }

        if (leitura.getTipoPeca() == TipoPeca.AMBAS) {
            leitura.adicionarAlerta(criarAlerta(leitura, TipoAlerta.CONTAGEM_ANOMALA,
                    "Conflito de sensores: indutivo e capacitivo detectaram a mesma peça"));
        }

        return leituraRepository.save(leitura);
    }

    @Transactional(readOnly = true)
    public Optional<LeituraSensores> buscarPorId(Long id) {
        return leituraRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<LeituraSensores> buscarPorPeriodo(Instant inicio, Instant fim) {
        return leituraRepository.buscarPorPeriodo(inicio, fim);
    }

    @Transactional(readOnly = true)
    public List<LeituraSensores> buscarPorTipoPeca(TipoPeca tipo) {
        return leituraRepository.buscarPorTipoPeca(tipo);
    }

    @Transactional(readOnly = true)
    public List<LeituraSensores> buscarPorStatusReservatorio(StatusReservatorio status) {
        return leituraRepository.buscarPorStatusReservatorio(status);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> listarTodas(Pageable paginacao) {
        return leituraRepository.listarMaisRecentes(paginacao);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> buscarPorPeriodo(Instant inicio, Instant fim, Pageable paginacao) {
        return leituraRepository.buscarPorPeriodo(inicio, fim, paginacao);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> buscarPorPeriodoETipoPeca(Instant inicio, Instant fim, TipoPeca tipo, Pageable paginacao) {
        return leituraRepository.buscarPorPeriodoETipoPeca(inicio, fim, tipo, paginacao);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> buscarPorPeriodoEStatusReservatorio(Instant inicio, Instant fim, StatusReservatorio status, Pageable paginacao) {
        return leituraRepository.buscarPorPeriodoEStatusReservatorio(inicio, fim, status, paginacao);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> buscarPorTipoPeca(TipoPeca tipo, Pageable paginacao) {
        return leituraRepository.buscarPorTipoPeca(tipo, paginacao);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> buscarPorStatusReservatorio(StatusReservatorio status, Pageable paginacao) {
        return leituraRepository.buscarPorStatusReservatorio(status, paginacao);
    }

    @Transactional
    public void excluirPorId(Long id) {
        leituraRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<AlertaNivel> buscarAlertasNaoResolvidos() {
        return alertaRepository.buscarNaoResolvidos();
    }

    @Transactional
    public void resolverAlerta(Long alertaId) {
        AlertaNivel alerta = alertaRepository.findById(alertaId)
                .orElseThrow(() -> new EntityNotFoundException("Alerta não encontrado: " + alertaId));
        alerta.marcarComoResolvido();
    }

    private void aplicarClassificacaoDaPeca(LeituraSensores leitura) {
        TipoPeca calculado = ClassificadorPeca.classificar(List.of(
                new SensorIndutivo(leitura.getIndutivoDetectouMetal(), leitura.getIndutivoIntensidade()),
                new SensorCapacitivo(leitura.getCapacitivoDetectouNaoMetal(), leitura.getCapacitivoIntensidade())));
        TipoPeca informado = leitura.getTipoPeca();
        if (informado != null && informado != calculado) {
            log.warn("Tipo informado pelo dispositivo {} ({}) diverge dos sensores ({}); usando o derivado dos sensores",
                    leitura.getDispositivoId(), informado, calculado);
        }
        leitura.setTipoPeca(calculado);
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

    private void resolverAlertasSuperados(String dispositivoId, TipoAlerta alertaAtual) {
        Set<TipoAlerta> superados = EnumSet.copyOf(ALERTAS_RESERVATORIO);
        if (alertaAtual != null) {
            superados.remove(alertaAtual);
        }
        alertaRepository.buscarAtivosPorTipos(superados, dispositivoId)
                .forEach(AlertaNivel::marcarComoResolvido);
    }

    private String mensagemReservatorio(LeituraSensores leitura) {
        String nivel = String.format(Locale.ROOT, "%.2f%%", leitura.getNivelReservatorioPercentual());
        return switch (leitura.getStatusReservatorio()) {
            case ALERTA_BAIXO -> "Nível do reservatório baixo: " + nivel;
            case ALERTA_ALTO -> "Nível do reservatório alto: " + nivel + " - programar esvaziamento";
            case CRITICO -> "Nível do reservatório crítico: " + nivel + " - risco de transbordo, esvaziar imediatamente";
            case NORMAL -> "Nível do reservatório normal: " + nivel;
        };
    }

    // O alerta herda o instante da leitura para manter o histórico coerente.
    private AlertaNivel criarAlerta(LeituraSensores leitura, TipoAlerta tipo, String mensagem) {
        Instant instante = leitura.getDataHora() != null ? leitura.getDataHora() : Instant.now();
        return new AlertaNivel(leitura, instante, tipo, mensagem, false);
    }
}
