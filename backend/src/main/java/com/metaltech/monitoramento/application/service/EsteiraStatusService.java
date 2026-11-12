package com.metaltech.monitoramento.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.TipoPeca;
import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EsteiraStatusService {

    private final LeituraSensoresRepository leituraRepository;
    private final AlertaNivelRepository alertaRepository;

    // Sem leituras, o tratador global devolve 404 NAO_ENCONTRADO.
    @Transactional(readOnly = true)
    public EsteiraStatus obterStatusAtual() {
        LeituraSensores ultima = leituraRepository.findFirstByOrderByDataHoraDescIdDesc()
                .orElseThrow(() -> new EntityNotFoundException("Nenhuma leitura registrada ainda"));
        return new EsteiraStatus(
                ultima.getDataHora(),
                ultima.getDispositivoId(),
                ultima.getNivelReservatorioPercentual(),
                ultima.getStatusReservatorio().name(),
                leituraRepository.countByTipoPeca(TipoPeca.METALICA),
                leituraRepository.countByTipoPeca(TipoPeca.NAO_METALICA),
                leituraRepository.countByTipoPeca(TipoPeca.DESCONHECIDA),
                leituraRepository.countByTipoPeca(TipoPeca.AMBAS),
                alertaRepository.contarNaoResolvidos());
    }
}
