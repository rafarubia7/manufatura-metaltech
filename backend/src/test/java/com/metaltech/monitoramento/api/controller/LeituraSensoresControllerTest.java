package com.metaltech.monitoramento.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaltech.monitoramento.api.dto.LeituraSensoresDTO;
import com.metaltech.monitoramento.api.dto.NivelReservatorioDTO;
import com.metaltech.monitoramento.api.dto.PecaDTO;
import com.metaltech.monitoramento.api.dto.SensorCapacitivoDTO;
import com.metaltech.monitoramento.api.dto.SensorDistanciaDTO;
import com.metaltech.monitoramento.api.dto.SensorIndutivoDTO;
import com.metaltech.monitoramento.api.mapper.LeituraSensoresMapper;
import com.metaltech.monitoramento.application.service.EstatisticasService;
import com.metaltech.monitoramento.application.service.LeituraSensoresService;
import com.metaltech.monitoramento.domain.model.AlertaNivel;
import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoAlerta;
import com.metaltech.monitoramento.domain.model.TipoPeca;

import jakarta.persistence.EntityNotFoundException;

@WebMvcTest(LeituraSensoresController.class)
@Import(LeituraSensoresMapper.class)
class LeituraSensoresControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LeituraSensoresService leituraService;

    @MockBean
    private EstatisticasService estatisticasService;

    private LeituraSensoresDTO dtoValido;
    private LeituraSensores leituraValida;

    @BeforeEach
    void preparar() {
        Instant agora = Instant.now();
        dtoValido = new LeituraSensoresDTO(
                agora,
                new SensorDistanciaDTO(12.5, "cm", "OK"),
                new SensorIndutivoDTO(true, 0.85, "OK"),
                new SensorCapacitivoDTO(false, 0.12, "OK"),
                new PecaDTO("peca-20240115-103045-001", "METALICA", agora),
                new NivelReservatorioDTO(75.5, 100.0, 75.5, "NORMAL"),
                "ESP32-001",
                "1.0.0");
        leituraValida = new LeituraSensores(1L, agora, 12.5, true, 0.85, false, 0.12, TipoPeca.METALICA,
                75.5, StatusReservatorio.NORMAL, "ESP32-001", "1.0.0", Collections.emptyList());
    }

    @Test
    void deveCriarLeituraComSucesso() throws Exception {
        when(leituraService.salvarLeitura(any(LeituraSensores.class))).thenReturn(leituraValida);

        mockMvc.perform(post("/api/v1/leituras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoValido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.peca.tipo").value("METALICA"))
                .andExpect(jsonPath("$.peca.timestampDetectado").exists())
                .andExpect(jsonPath("$.nivelReservatorio.status").value("NORMAL"));
    }

    @Test
    void deveRetornarBadRequestQuandoDtoInvalido() throws Exception {
        LeituraSensoresDTO dtoInvalido = new LeituraSensoresDTO(null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/leituras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigoErro").value("ERRO_VALIDACAO"));
    }

    @Test
    void deveRetornarBadRequestQuandoTipoPecaInvalido() throws Exception {
        dtoValido.getPeca().setTipo("PLASTICO");

        mockMvc.perform(post("/api/v1/leituras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoValido)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveListarLeiturasComFiltros() throws Exception {
        when(leituraService.buscarPorPeriodo(any(Instant.class), any(Instant.class), any()))
                .thenReturn(new PageImpl<>(List.of(leituraValida)));

        mockMvc.perform(get("/api/v1/leituras")
                        .param("inicio", Instant.now().minusSeconds(3600).toString())
                        .param("fim", Instant.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].id").value(1))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void deveBuscarLeituraPorIdExistente() throws Exception {
        when(leituraService.buscarPorId(1L)).thenReturn(Optional.of(leituraValida));

        mockMvc.perform(get("/api/v1/leituras/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deveRetornarNotFoundQuandoIdNaoExiste() throws Exception {
        when(leituraService.buscarPorId(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/leituras/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveListarAlertasNaoResolvidos() throws Exception {
        when(leituraService.buscarAlertasNaoResolvidos()).thenReturn(List.of(
                new AlertaNivel(1L, leituraValida, Instant.now(), TipoAlerta.RESERVATORIO_ALTO, "Teste", false)));

        mockMvc.perform(get("/api/v1/leituras/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].tipoAlerta").value("RESERVATORIO_ALTO"))
                .andExpect(jsonPath("$[0].leituraId").value(1))
                .andExpect(jsonPath("$[0].dispositivoId").value("ESP32-001"));
    }

    @Test
    void deveResolverAlertaComSucesso() throws Exception {
        doNothing().when(leituraService).resolverAlerta(1L);

        mockMvc.perform(put("/api/v1/leituras/alertas/1/resolver"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deveRetornarNotFoundAoResolverAlertaInexistente() throws Exception {
        doThrow(new EntityNotFoundException("Alerta não encontrado: 99")).when(leituraService).resolverAlerta(99L);

        mockMvc.perform(put("/api/v1/leituras/alertas/99/resolver"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigoErro").value("NAO_ENCONTRADO"));
    }

    @Test
    void deveRetornarEstatisticas() throws Exception {
        when(estatisticasService.calcularEstatisticas(isNull(), isNull()))
                .thenReturn(Map.of("totalLeituras", 100, "totalMetalicas", 60, "totalNaoMetalicas", 40));

        mockMvc.perform(get("/api/v1/leituras/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeituras").value(100));
    }

    @Test
    void deveRetornarBadRequestQuandoPeriodoIncompleto() throws Exception {
        mockMvc.perform(get("/api/v1/leituras/estatisticas").param("inicio", Instant.now().toString()))
                .andExpect(status().isBadRequest());
    }
}
