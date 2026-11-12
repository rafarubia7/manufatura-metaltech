package com.metaltech.monitoramento.api.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.metaltech.monitoramento.api.dto.AlertaNivelRespostaDTO;
import com.metaltech.monitoramento.api.dto.LeituraSensoresDTO;
import com.metaltech.monitoramento.api.dto.LeituraSensoresRespostaDTO;
import com.metaltech.monitoramento.api.dto.NivelReservatorioDTO;
import com.metaltech.monitoramento.api.dto.PecaDTO;
import com.metaltech.monitoramento.api.dto.SensorCapacitivoDTO;
import com.metaltech.monitoramento.api.dto.SensorDistanciaDTO;
import com.metaltech.monitoramento.api.dto.SensorIndutivoDTO;
import com.metaltech.monitoramento.domain.model.AlertaNivel;
import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoPeca;

@Component
public class LeituraSensoresMapper {

    private static final double CAPACIDADE_MAXIMA = 100.0;

    public LeituraSensores paraEntidade(LeituraSensoresDTO dto) {
        if (dto == null) {
            return null;
        }
        LeituraSensores leitura = new LeituraSensores();
        leitura.setDataHora(dto.getDataHora());
        leitura.setDistanciaCm(dto.getSensorDistancia().getValor());
        leitura.setIndutivoDetectouMetal(dto.getSensorIndutivo().getDetectouMetal());
        leitura.setIndutivoIntensidade(dto.getSensorIndutivo().getIntensidade());
        leitura.setCapacitivoDetectouNaoMetal(dto.getSensorCapacitivo().getDetectouNaoMetal());
        leitura.setCapacitivoIntensidade(dto.getSensorCapacitivo().getIntensidade());
        leitura.setTipoPeca(converterTipoPeca(dto.getPeca().getTipo()));
        leitura.setNivelReservatorioPercentual(dto.getNivelReservatorio().getPercentual());
        leitura.setStatusReservatorio(converterStatusReservatorio(dto.getNivelReservatorio().getStatus()));
        leitura.setDispositivoId(dto.getDispositivoId());
        leitura.setVersaoFirmware(dto.getVersaoFirmware());
        return leitura;
    }

    public LeituraSensoresRespostaDTO paraRespostaDto(LeituraSensores leitura) {
        if (leitura == null) {
            return null;
        }
        LeituraSensoresRespostaDTO dto = new LeituraSensoresRespostaDTO();
        dto.setId(leitura.getId());
        dto.setDataHora(leitura.getDataHora());
        dto.setSensorDistancia(new SensorDistanciaDTO(leitura.getDistanciaCm(), "cm", "OK"));
        dto.setSensorIndutivo(new SensorIndutivoDTO(leitura.getIndutivoDetectouMetal(), leitura.getIndutivoIntensidade(), "OK"));
        dto.setSensorCapacitivo(new SensorCapacitivoDTO(leitura.getCapacitivoDetectouNaoMetal(), leitura.getCapacitivoIntensidade(), "OK"));
        dto.setPeca(new PecaDTO("peca-" + leitura.getId(),
                leitura.getTipoPeca() != null ? leitura.getTipoPeca().name() : null,
                leitura.getDataHora()));
        dto.setNivelReservatorio(new NivelReservatorioDTO(
                leitura.getNivelReservatorioPercentual(),
                CAPACIDADE_MAXIMA,
                leitura.getNivelReservatorioPercentual(),
                leitura.getStatusReservatorio() != null ? leitura.getStatusReservatorio().name() : StatusReservatorio.NORMAL.name()));
        dto.setDispositivoId(leitura.getDispositivoId());
        dto.setVersaoFirmware(leitura.getVersaoFirmware());
        dto.setAlertas(paraListaAlertasRespostaDto(leitura.getAlertas()));
        return dto;
    }

    public AlertaNivelRespostaDTO paraRespostaDto(AlertaNivel alerta) {
        if (alerta == null) {
            return null;
        }
        AlertaNivelRespostaDTO dto = new AlertaNivelRespostaDTO();
        dto.setId(alerta.getId());
        if (alerta.getLeitura() != null) {
            dto.setLeituraId(alerta.getLeitura().getId());
            dto.setDispositivoId(alerta.getLeitura().getDispositivoId());
        }
        dto.setDataHoraAlerta(alerta.getDataHoraAlerta());
        dto.setTipoAlerta(alerta.getTipoAlerta() != null ? alerta.getTipoAlerta().name() : null);
        dto.setMensagem(alerta.getMensagem());
        dto.setResolvido(alerta.getResolvido());
        return dto;
    }

    public List<AlertaNivelRespostaDTO> paraListaAlertasRespostaDto(List<AlertaNivel> alertas) {
        if (alertas == null) {
            return List.of();
        }
        return alertas.stream().map(this::paraRespostaDto).toList();
    }

    private TipoPeca converterTipoPeca(String valor) {
        if (valor == null || valor.isBlank()) {
            return TipoPeca.DESCONHECIDA;
        }
        return TipoPeca.valueOf(valor);
    }

    // O status do dispositivo é só informativo (o service recalcula); valor desconhecido vira nulo.
    private StatusReservatorio converterStatusReservatorio(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return StatusReservatorio.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
