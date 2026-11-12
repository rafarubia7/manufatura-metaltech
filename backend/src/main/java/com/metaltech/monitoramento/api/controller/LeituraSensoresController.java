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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.metaltech.monitoramento.api.dto.AlertaNivelRespostaDTO;
import com.metaltech.monitoramento.api.dto.LeituraSensoresDTO;
import com.metaltech.monitoramento.api.dto.LeituraSensoresRespostaDTO;
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
    public ResponseEntity<LeituraSensoresRespostaDTO> criarLeitura(@Valid @RequestBody LeituraSensoresDTO dto) {
        LeituraSensores salva = leituraService.salvarLeitura(mapper.paraEntidade(dto));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.paraRespostaDto(salva));
    }

    @GetMapping
    @Operation(summary = "Lista leituras com paginação e filtros opcionais por período, tipo de peça e status")
    public ResponseEntity<Map<String, Object>> listarLeituras(
            @RequestParam(name = "inicio", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant inicio,
            @RequestParam(name = "fim", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant fim,
            @RequestParam(name = "tipoPeca", required = false) TipoPeca tipoPeca,
            @RequestParam(name = "statusReservatorio", required = false) StatusReservatorio statusReservatorio,
            @PageableDefault(size = 100, sort = "dataHora") Pageable paginacao) {

        boolean comPeriodo = inicio != null && fim != null;
        Page<LeituraSensores> pagina;
        if (comPeriodo && tipoPeca != null) {
            pagina = leituraService.buscarPorPeriodoETipoPeca(inicio, fim, tipoPeca, paginacao);
        } else if (comPeriodo && statusReservatorio != null) {
            pagina = leituraService.buscarPorPeriodoEStatusReservatorio(inicio, fim, statusReservatorio, paginacao);
        } else if (comPeriodo) {
            pagina = leituraService.buscarPorPeriodo(inicio, fim, paginacao);
        } else if (tipoPeca != null) {
            pagina = leituraService.buscarPorTipoPeca(tipoPeca, paginacao);
        } else if (statusReservatorio != null) {
            pagina = leituraService.buscarPorStatusReservatorio(statusReservatorio, paginacao);
        } else {
            pagina = leituraService.listarTodas(paginacao);
        }

        List<LeituraSensoresRespostaDTO> conteudo = pagina.getContent().stream()
                .map(mapper::paraRespostaDto)
                .toList();

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("conteudo", conteudo);
        resposta.put("paginacao", Map.of(
                "numeroPagina", pagina.getNumber(),
                "tamanhoPagina", pagina.getSize(),
                "ordenacao", pagina.getSort().toString(),
                "deslocamento", pagina.getPageable().isPaged() ? pagina.getPageable().getOffset() : 0L));
        resposta.put("totalElementos", pagina.getTotalElements());
        resposta.put("totalPaginas", pagina.getTotalPages());
        resposta.put("ultima", pagina.isLast());
        resposta.put("primeira", pagina.isFirst());
        resposta.put("quantidadeElementos", pagina.getNumberOfElements());
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma leitura pelo id")
    public ResponseEntity<LeituraSensoresRespostaDTO> buscarPorId(@PathVariable("id") Long id) {
        return leituraService.buscarPorId(id)
                .map(mapper::paraRespostaDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corrige uma leitura existente (o timestamp não muda)",
            description = "Tipo da peça, status do reservatório e alertas são recalculados como no POST.")
    public ResponseEntity<LeituraSensoresRespostaDTO> atualizarLeitura(
            @PathVariable("id") Long id, @Valid @RequestBody LeituraSensoresDTO dto) {
        LeituraSensores atualizada = leituraService.atualizarLeitura(id, mapper.paraEntidade(dto));
        return ResponseEntity.ok(mapper.paraRespostaDto(atualizada));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui uma leitura e os alertas gerados por ela")
    public ResponseEntity<Void> excluirLeitura(@PathVariable("id") Long id) {
        leituraService.excluirPorId(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/alertas")
    @Operation(summary = "Lista os alertas ainda não resolvidos, do mais recente para o mais antigo")
    public ResponseEntity<List<AlertaNivelRespostaDTO>> listarAlertasNaoResolvidos() {
        return ResponseEntity.ok(mapper.paraListaAlertasRespostaDto(leituraService.buscarAlertasNaoResolvidos()));
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
    public ResponseEntity<Map<String, Object>> obterEstatisticas(
            @RequestParam(name = "inicio", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant inicio,
            @RequestParam(name = "fim", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant fim) {

        if ((inicio == null) != (fim == null)) {
            throw new IllegalArgumentException("Informe 'inicio' e 'fim' juntos, ou nenhum dos dois");
        }
        return ResponseEntity.ok(estatisticasService.calcularEstatisticas(inicio, fim));
    }
}
