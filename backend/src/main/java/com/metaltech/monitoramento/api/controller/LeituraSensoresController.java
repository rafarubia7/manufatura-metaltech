package com.metaltech.monitoramento.api.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.metaltech.monitoramento.api.dto.AlertaNivelResponseDTO;
import com.metaltech.monitoramento.api.dto.LeituraSensoresDTO;
import com.metaltech.monitoramento.api.dto.LeituraSensoresResponseDTO;
import com.metaltech.monitoramento.api.mapper.LeituraSensoresMapper;
import com.metaltech.monitoramento.application.service.EstatisticasService;
import com.metaltech.monitoramento.application.service.LeituraSensoresService;
import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoPeca;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/leituras")
@RequiredArgsConstructor
@Tag(name = "Leituras da esteira", description = "Recebimento de leituras dos sensores, alertas e estatísticas operacionais")
public class LeituraSensoresController {

    private final LeituraSensoresService leituraService;
    private final LeituraSensoresMapper mapper;
    private final EstatisticasService estatisticasService;

    @PostMapping
    @Operation(summary = "Registra uma leitura dos sensores",
            description = "O status do reservatório é recalculado pelo backend a partir do percentual "
                    + "(baixo < 20%, alto > 80%, crítico >= 95%). Alertas são gerados automaticamente.")
    public ResponseEntity<LeituraSensoresResponseDTO> criarLeitura(@Valid @RequestBody LeituraSensoresDTO dto) {
        LeituraSensores salva = leituraService.salvarLeitura(mapper.toEntity(dto));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDto(salva));
    }

    @GetMapping
    @Operation(summary = "Lista leituras com paginação e filtros opcionais por período, tipo de peça e status")
    public ResponseEntity<Map<String, Object>> listarLeituras(
            @RequestParam(name = "inicio", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant inicio,
            @RequestParam(name = "fim", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant fim,
            @RequestParam(name = "tipoPeca", required = false) TipoPeca tipoPeca,
            @RequestParam(name = "statusReservatorio", required = false) StatusReservatorio statusReservatorio,
            @PageableDefault(size = 100, sort = "timestamp") Pageable pageable) {

        Page<LeituraSensores> page;

        if (inicio != null && fim != null && tipoPeca != null) {
            page = leituraService.findByTimestampAndTipoPeca(inicio, fim, tipoPeca, pageable);
        } else if (inicio != null && fim != null && statusReservatorio != null) {
            page = leituraService.findByTimestampAndStatusReservatorio(inicio, fim, statusReservatorio, pageable);
        } else if (inicio != null && fim != null) {
            page = leituraService.findByTimestampBetween(inicio, fim, pageable);
        } else if (tipoPeca != null) {
            page = leituraService.findByTipoPeca(tipoPeca, pageable);
        } else if (statusReservatorio != null) {
            page = leituraService.findByStatusReservatorio(statusReservatorio, pageable);
        } else {
            page = leituraService.listarTodas(pageable);
        }

        List<LeituraSensoresResponseDTO> content = page.getContent().stream()
                .map(mapper::toResponseDto)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);

        long offset = page.getPageable().isPaged() ? page.getPageable().getOffset() : 0L;
        response.put("pageable", Map.of(
                "pageNumber", page.getNumber(),
                "pageSize", page.getSize(),
                "sort", page.getSort().toString(),
                "offset", offset));
        response.put("totalElements", page.getTotalElements());
        response.put("totalPages", page.getTotalPages());
        response.put("last", page.isLast());
        response.put("first", page.isFirst());
        response.put("numberOfElements", page.getNumberOfElements());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma leitura pelo id")
    public ResponseEntity<LeituraSensoresResponseDTO> buscarPorId(@PathVariable("id") Long id) {
        return leituraService.buscarPorId(id)
                .map(mapper::toResponseDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/alertas")
    @Operation(summary = "Lista os alertas ainda não resolvidos, do mais recente para o mais antigo")
    public ResponseEntity<List<AlertaNivelResponseDTO>> listarAlertasNaoResolvidos() {
        return ResponseEntity.ok(mapper.toAlertaResponseDtoList(leituraService.buscarAlertasNaoResolvidos()));
    }

    @PutMapping("/alertas/{id}/resolver")
    @Operation(summary = "Marca um alerta como resolvido (ação do operador)")
    public ResponseEntity<Void> resolverAlerta(@PathVariable("id") Long id) {
        leituraService.resolverAlerta(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/estatisticas")
    @Operation(summary = "Indicadores operacionais do reservatório e da classificação de peças",
            description = "Sem 'inicio' e 'fim', considera todo o histórico persistido.")
    public ResponseEntity<Map<String, Object>> estatisticasResumo(
            @RequestParam(name = "inicio", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant inicio,
            @RequestParam(name = "fim", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant fim) {

        if ((inicio == null) != (fim == null)) {
            throw new IllegalArgumentException("Informe 'inicio' e 'fim' juntos, ou nenhum dos dois");
        }
        return ResponseEntity.ok(estatisticasService.calcularEstatisticas(inicio, fim));
    }
}
