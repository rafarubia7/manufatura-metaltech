package com.metaltech.monitoramento.api.mapper;

import com.metaltech.monitoramento.api.dto.AlertaNivelResponseDTO;
import com.metaltech.monitoramento.api.dto.LeituraSensoresDTO;
import com.metaltech.monitoramento.api.dto.LeituraSensoresResponseDTO;
import com.metaltech.monitoramento.api.dto.NivelReservatorioDTO;
import com.metaltech.monitoramento.api.dto.PecaDTO;
import com.metaltech.monitoramento.api.dto.SensorCapacitivoDTO;
import com.metaltech.monitoramento.api.dto.SensorDistanciaDTO;
import com.metaltech.monitoramento.api.dto.SensorIndutivoDTO;
import com.metaltech.monitoramento.domain.model.AlertaNivel;
import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoAlerta;
import com.metaltech.monitoramento.domain.model.TipoPeca;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LeituraSensoresMapper {

    public LeituraSensores toEntity(LeituraSensoresDTO dto) {
        if (dto == null) {
            return null;
        }

        LeituraSensores entity = new LeituraSensores();
        entity.setTimestamp(dto.getTimestamp());
        entity.setDistanciaCm(dto.getSensorDistancia() != null ? dto.getSensorDistancia().getValor() : null);
        entity.setIndutivoDetectouMetal(dto.getSensorIndutivo() != null ? dto.getSensorIndutivo().getDetectouMetal() : null);
        entity.setIndutivoIntensidade(dto.getSensorIndutivo() != null ? dto.getSensorIndutivo().getIntensidade() : null);
        entity.setCapacitivoDetectouNaoMetal(dto.getSensorCapacitivo() != null ? dto.getSensorCapacitivo().getDetectouNaoMetal() : null);
        entity.setCapacitivoIntensidade(dto.getSensorCapacitivo() != null ? dto.getSensorCapacitivo().getIntensidade() : null);
        entity.setTipoPeca(parseTipoPeca(dto.getPeca() != null ? dto.getPeca().getTipo() : null));
        entity.setNivelReservatorioPercentual(dto.getNivelReservatorio() != null ? dto.getNivelReservatorio().getPercentual() : null);
        entity.setStatusReservatorio(parseStatusReservatorio(dto.getNivelReservatorio() != null ? dto.getNivelReservatorio().getStatus() : null));
        entity.setDispositivoId(dto.getDispositivoId());
        entity.setVersaoFirmware(dto.getVersaoFirmware());
        return entity;
    }

    public LeituraSensoresResponseDTO toResponseDto(LeituraSensores entity) {
        if (entity == null) {
            return null;
        }

        LeituraSensoresResponseDTO dto = new LeituraSensoresResponseDTO();
        dto.setId(entity.getId());
        dto.setTimestamp(entity.getTimestamp());

        SensorDistanciaDTO distancia = new SensorDistanciaDTO();
        distancia.setValor(entity.getDistanciaCm());
        distancia.setUnidade("cm");
        distancia.setStatus("OK");
        dto.setSensorDistancia(distancia);

        SensorIndutivoDTO indutivo = new SensorIndutivoDTO();
        indutivo.setDetectouMetal(entity.getIndutivoDetectouMetal());
        indutivo.setIntensidade(entity.getIndutivoIntensidade());
        indutivo.setStatus("OK");
        dto.setSensorIndutivo(indutivo);

        SensorCapacitivoDTO capacitivo = new SensorCapacitivoDTO();
        capacitivo.setDetectouNaoMetal(entity.getCapacitivoDetectouNaoMetal());
        capacitivo.setIntensidade(entity.getCapacitivoIntensidade());
        capacitivo.setStatus("OK");
        dto.setSensorCapacitivo(capacitivo);

        PecaDTO peca = new PecaDTO();
        peca.setId("peca-" + entity.getId());
        peca.setTipo(entity.getTipoPeca() != null ? entity.getTipoPeca().name() : null);
        peca.setTimestampDetectado(entity.getTimestamp());
        dto.setPeca(peca);

        NivelReservatorioDTO nivel = new NivelReservatorioDTO();
        nivel.setNivelAtual(entity.getNivelReservatorioPercentual());
        nivel.setCapacidadeMaxima(100.0);
        nivel.setPercentual(entity.getNivelReservatorioPercentual());
        nivel.setStatus(entity.getStatusReservatorio() != null ? entity.getStatusReservatorio().name() : "NORMAL");
        dto.setNivelReservatorio(nivel);

        dto.setDispositivoId(entity.getDispositivoId());
        dto.setVersaoFirmware(entity.getVersaoFirmware());

        if (entity.getAlertas() == null || entity.getAlertas().isEmpty()) {
            dto.setAlertas(List.of());
        } else {
            dto.setAlertas(entity.getAlertas().stream().map(this::toResponseDto).collect(Collectors.toList()));
        }

        return dto;
    }

    public List<LeituraSensoresResponseDTO> toResponseDtoList(List<LeituraSensores> entities) {
        if (entities == null) {
            return null;
        }
        return entities.stream().map(this::toResponseDto).collect(Collectors.toList());
    }

    public AlertaNivelResponseDTO toResponseDto(AlertaNivel entity) {
        if (entity == null) {
            return null;
        }

        AlertaNivelResponseDTO dto = new AlertaNivelResponseDTO();
        dto.setId(entity.getId());
        dto.setTimestampAlertado(entity.getTimestampAlertado());
        dto.setTipoAlerta(entity.getTipoAlerta() != null ? entity.getTipoAlerta().name() : null);
        dto.setMensagem(entity.getMensagem());
        dto.setResolvido(entity.getResolvido());
        return dto;
    }

    public List<AlertaNivelResponseDTO> toAlertaResponseDtoList(List<AlertaNivel> entities) {
        if (entities == null) {
            return null;
        }
        return entities.stream().map(this::toResponseDto).collect(Collectors.toList());
    }

    private TipoPeca parseTipoPeca(String value) {
        if (value == null || value.isBlank()) {
            return TipoPeca.DESCONHECIDA;
        }
        return TipoPeca.valueOf(value);
    }

    private StatusReservatorio parseStatusReservatorio(String value) {
        if (value == null || value.isBlank()) {
            return StatusReservatorio.NORMAL;
        }
        return StatusReservatorio.valueOf(value);
    }
}