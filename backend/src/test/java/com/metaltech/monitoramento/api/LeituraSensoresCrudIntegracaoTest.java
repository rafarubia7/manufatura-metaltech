package com.metaltech.monitoramento.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeituraSensoresCrudIntegracaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LeituraSensoresRepository leituraRepository;

    @Autowired
    private AlertaNivelRepository alertaRepository;

    @BeforeEach
    void limparBase() {
        alertaRepository.deleteAll();
        leituraRepository.deleteAll();
    }

    private String corpo(String timestamp, double percentual) {
        return """
            {
              "timestamp": "%s",
              "sensorDistancia": {"valor": 9.5, "unidade": "cm", "status": "OK"},
              "sensorIndutivo": {"detectouMetal": true, "intensidade": 0.82, "status": "OK"},
              "sensorCapacitivo": {"detectouNaoMetal": false, "intensidade": 0.06, "status": "OK"},
              "peca": {"id": "peca-1", "tipo": "METALICA", "timestampDetectado": "%s"},
              "nivelReservatorio": {"nivelAtual": %s, "capacidadeMaxima": 100.0, "percentual": %s, "status": "NORMAL"},
              "dispositivoId": "ESP32-001",
              "versaoFirmware": "1.0.0"
            }
            """.formatted(timestamp, timestamp, percentual, percentual);
    }

    private long criar(double percentual) throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/leituras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("2026-11-03T08:00:00Z", percentual)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("id").asLong();
    }

    @Test
    void deveAtualizarLeituraRecalculandoStatusEAlertasSemMudarOTimestamp() throws Exception {
        long id = criar(50.0);

        mockMvc.perform(put("/api/v1/leituras/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("2030-01-01T00:00:00Z", 97.0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").value("2026-11-03T08:00:00Z"))
                .andExpect(jsonPath("$.nivelReservatorio.status").value("CRITICO"))
                .andExpect(jsonPath("$.alertas[0].tipoAlerta").value("RESERVATORIO_CRITICO"));

        assertThat(leituraRepository.count()).isEqualTo(1);
        assertThat(alertaRepository.count()).isEqualTo(1);
    }

    @Test
    void deveRetornarNotFoundAoAtualizarLeituraInexistente() throws Exception {
        mockMvc.perform(put("/api/v1/leituras/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("2026-11-03T08:00:00Z", 50.0)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveExcluirLeituraComSeusAlertas() throws Exception {
        long id = criar(97.0);
        assertThat(alertaRepository.count()).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/leituras/" + id)).andExpect(status().isNoContent());

        assertThat(leituraRepository.count()).isZero();
        assertThat(alertaRepository.count()).isZero();
        mockMvc.perform(delete("/api/v1/leituras/" + id)).andExpect(status().isNotFound());
    }
}
