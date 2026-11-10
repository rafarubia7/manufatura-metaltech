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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.metaltech.monitoramento.api.dto.LeituraSensoresDTO;
import com.metaltech.monitoramento.api.dto.LeituraSensoresResponseDTO;
import com.metaltech.monitoramento.api.mapper.LeituraSensoresMapper;
import com.metaltech.monitoramento.application.service.EstatisticasService;
import com.metaltech.monitoramento.application.service.LeituraSensoresService;
import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoPeca;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/leituras")
@RequiredArgsConstructor
public class LeituraSensoresController {

    private final LeituraSensoresService leituraService;
    private final LeituraSensoresMapper mapper;
    private final EstatisticasService estatisticasService;

    @PostMapping
    public ResponseEntity<LeituraSensoresResponseDTO> criarLeitura(@Valid @RequestBody LeituraSensoresDTO dto) {
        LeituraSensores leitura = mapper.toEntity(dto);
        LeituraSensores salva = leituraService.salvarLeitura(leitura);
        return ResponseEntity.ok(mapper.toResponseDto(salva));
    }

    @GetMapping
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
    public ResponseEntity<LeituraSensoresResponseDTO> buscarPorId(@PathVariable("id") Long id) {
        return leituraService.buscarPorId(id)
                .map(mapper::toResponseDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/alertas")
    public ResponseEntity<List<com.metaltech.monitoramento.domain.model.AlertaNivel>> listarAlertasNaoResolvidos() {
        return ResponseEntity.ok(leituraService.buscarAlertasNaoResolvidos());
    }

    @PutMapping("/alertas/{id}/resolver")
    public ResponseEntity<Void> resolverAlerta(@PathVariable("id") Long id) {
        leituraService.resolverAlerta(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/estatisticas")
    public ResponseEntity<Map<String, Object>> estatisticasResumo(
            @RequestParam(name = "inicio", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant inicio,
            @RequestParam(name = "fim", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant fim) {

        Instant inicioEfetivo = inicio != null ? inicio : Instant.now().minusSeconds(3600);
        Instant fimEfetivo = fim != null ? fim : Instant.now();

        return ResponseEntity.ok(estatisticasService.calcularEstatisticas(inicioEfetivo, fimEfetivo));
    }
}