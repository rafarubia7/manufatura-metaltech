package com.metaltech.monitoramento.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.metaltech.monitoramento.application.service.EsteiraStatus;
import com.metaltech.monitoramento.application.service.EsteiraStatusService;

import jakarta.persistence.EntityNotFoundException;

@WebMvcTest(EsteiraController.class)
class EsteiraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EsteiraStatusService statusService;

    @Test
    void deveRetornarStatusAtual() throws Exception {
        when(statusService.obterStatusAtual()).thenReturn(new EsteiraStatus(
                Instant.parse("2026-11-03T10:00:00Z"), "ESP32-001", 82.5, "ALERTA_ALTO", 6, 3, 1, 0, 2));

        mockMvc.perform(get("/api/v1/esteira/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispositivoId").value("ESP32-001"))
                .andExpect(jsonPath("$.nivelReservatorioPercentual").value(82.5))
                .andExpect(jsonPath("$.statusReservatorio").value("ALERTA_ALTO"))
                .andExpect(jsonPath("$.totalMetalicas").value(6))
                .andExpect(jsonPath("$.alertasAtivos").value(2));
    }

    @Test
    void deveRetornarNotFoundSemLeituras() throws Exception {
        when(statusService.obterStatusAtual()).thenThrow(new EntityNotFoundException("Nenhuma leitura registrada ainda"));

        mockMvc.perform(get("/api/v1/esteira/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigoErro").value("NAO_ENCONTRADO"));
    }
}
