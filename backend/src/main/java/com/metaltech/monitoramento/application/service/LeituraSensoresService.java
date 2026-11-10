package com.metaltech.monitoramento.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.metaltech.monitoramento.domain.model.AlertaNivel;
import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.TipoAlerta;
import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeituraSensoresService {

    private final LeituraSensoresRepository leituraRepository;
    private final AlertaNivelRepository alertaRepository;

    @Transactional
    public LeituraSensores salvarLeitura(LeituraSensores leitura) {
        List<AlertaNivel> alertas = gerarAlertas(leitura);
        registrarAlertas(leitura, alertas);
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
    public List<LeituraSensores> buscarPorTipoPeca(com.metaltech.monitoramento.domain.model.TipoPeca tipo) {
        return leituraRepository.findByTipoPeca(tipo);
    }

    @Transactional(readOnly = true)
    public List<LeituraSensores> buscarPorStatusReservatorio(com.metaltech.monitoramento.domain.model.StatusReservatorio status) {
        return leituraRepository.findByStatusReservatorio(status);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> listarTodas(Pageable pageable) {
        return leituraRepository.findAllByOrderByTimestampDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> findByTimestampAndTipoPeca(Instant inicio, Instant fim, com.metaltech.monitoramento.domain.model.TipoPeca tipo, Pageable pageable) {
        return leituraRepository.findByTimestampAndTipoPeca(inicio, fim, tipo, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> findByTimestampAndStatusReservatorio(Instant inicio, Instant fim, com.metaltech.monitoramento.domain.model.StatusReservatorio status, Pageable pageable) {
        return leituraRepository.findByTimestampAndStatusReservatorio(inicio, fim, status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> findByTimestampBetween(Instant inicio, Instant fim, Pageable pageable) {
        return leituraRepository.findByTimestampBetween(inicio, fim, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> findByTipoPeca(com.metaltech.monitoramento.domain.model.TipoPeca tipo, Pageable pageable) {
        return leituraRepository.findByTipoPeca(tipo, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeituraSensores> findByStatusReservatorio(com.metaltech.monitoramento.domain.model.StatusReservatorio status, Pageable pageable) {
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
        alertaRepository.findById(alertaId).ifPresent(AlertaNivel::marcarComoResolvido);
    }

    private List<AlertaNivel> gerarAlertas(LeituraSensores leitura) {
        var alertas = new java.util.ArrayList<AlertaNivel>();

        if (leitura.getStatusReservatorio() == com.metaltech.monitoramento.domain.model.StatusReservatorio.ALERTA_BAIXO) {
            alertas.add(criarAlerta(leitura, TipoAlerta.RESERVATORIO_BAIXO,
                "Nível do reservatório baixo: " + leitura.getNivelReservatorioPercentual() + "%"));
        }

        if (leitura.getStatusReservatorio() == com.metaltech.monitoramento.domain.model.StatusReservatorio.ALERTA_ALTO) {
            alertas.add(criarAlerta(leitura, TipoAlerta.RESERVATORIO_ALTO,
                "Nível do reservatório alto: " + leitura.getNivelReservatorioPercentual() + "%"));
        }

        if (leitura.getStatusReservatorio() == com.metaltech.monitoramento.domain.model.StatusReservatorio.CRITICO) {
            alertas.add(criarAlerta(leitura, TipoAlerta.RESERVATORIO_CRITICO,
                "Nível do reservatório crítico: " + leitura.getNivelReservatorioPercentual() + "%"));
        }

        if (leitura.getTipoPeca() == com.metaltech.monitoramento.domain.model.TipoPeca.AMBAS) {
            alertas.add(criarAlerta(leitura, TipoAlerta.CONTAGEM_ANOMALA,
                "Conflito de sensores: ambos detectaram peça simultaneamente"));
        }

        return alertas;
    }

    private void registrarAlertas(LeituraSensores leitura, List<AlertaNivel> alertas) {
        for (AlertaNivel alerta : alertas) {
            alertaRepository.save(alerta);
            leitura.adicionarAlerta(alerta);
        }
    }

    private AlertaNivel criarAlerta(LeituraSensores leitura, TipoAlerta tipo, String mensagem) {
        return new AlertaNivel(null, leitura, Instant.now(), tipo, mensagem, false);
    }
}