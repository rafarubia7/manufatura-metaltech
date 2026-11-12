package com.metaltech.monitoramento.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

// Prova que Flyway + entidades + converters funcionam no MySQL real. Pula se o Docker não estiver ativo.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class LeituraSensoresMySqlIntegracaoTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void devePersistirLeituraEGerarAlertaNoMySql() throws Exception {
        mockMvc.perform(post("/api/v1/leituras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("2026-11-03T08:00:00Z", 97.0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nivelReservatorio.status").value("CRITICO"));

        assertThat(leituraRepository.count()).isEqualTo(1);
        assertThat(alertaRepository.contarNaoResolvidos()).isEqualTo(1);
    }

    @Test
    void deveConsultarPorPeriodoEEstatisticasNoMySql() throws Exception {
        mockMvc.perform(post("/api/v1/leituras").contentType(MediaType.APPLICATION_JSON)
                .content(corpo("2026-11-03T08:00:00Z", 30.0))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/leituras").contentType(MediaType.APPLICATION_JSON)
                .content(corpo("2026-11-03T08:01:00Z", 31.0))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/leituras")
                        .param("inicio", "2026-11-03T08:00:00Z")
                        .param("fim", "2026-11-03T08:00:30Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));

        mockMvc.perform(get("/api/v1/leituras/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeituras").value(2))
                .andExpect(jsonPath("$.totalMetalicas").value(2));
    }
}
