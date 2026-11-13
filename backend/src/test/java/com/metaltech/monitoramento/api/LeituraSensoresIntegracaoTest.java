package com.metaltech.monitoramento.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

// Fluxo completo sem mocks: HTTP -> controller -> service -> JPA -> H2.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeituraSensoresIntegracaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LeituraSensoresRepository leituraRepository;

    @Autowired
    private AlertaNivelRepository alertaRepository;

    private Instant base;

    @BeforeEach
    void limparBase() {
        alertaRepository.deleteAll();
        leituraRepository.deleteAll();
        base = Instant.parse("2026-11-03T08:00:00Z");
    }

    private String corpoJson(int minuto, String tipo, double percentual, String statusInformado) {
        Instant instante = base.plusSeconds(60L * minuto);
        boolean metal = tipo.equals("METALICA") || tipo.equals("AMBAS");
        boolean naoMetal = tipo.equals("NAO_METALICA") || tipo.equals("AMBAS");
        return """
            {
              "timestamp": "%s",
              "sensorDistancia": {"valor": 9.5, "unidade": "cm", "status": "OK"},
              "sensorIndutivo": {"detectouMetal": %s, "intensidade": %s, "status": "OK"},
              "sensorCapacitivo": {"detectouNaoMetal": %s, "intensidade": %s, "status": "OK"},
              "peca": {"id": "peca-%d", "tipo": "%s", "timestampDetectado": "%s"},
              "nivelReservatorio": {"nivelAtual": %s, "capacidadeMaxima": 100.0, "percentual": %s, "status": "%s"},
              "dispositivoId": "ESP32-001",
              "versaoFirmware": "1.0.0"
            }
            """.formatted(instante, metal, metal ? 0.82 : 0.08, naoMetal, naoMetal ? 0.79 : 0.06,
                minuto, tipo, instante, percentual, percentual, statusInformado);
    }

    private JsonNode postar(String json) throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/leituras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta);
    }

    @Test
    void devePersistirLeituraCriticaComAlertaERecalcularStatus() throws Exception {
        JsonNode resposta = postar(corpoJson(0, "METALICA", 97.0, "NORMAL"));

        assertThat(resposta.at("/nivelReservatorio/status").asText()).isEqualTo("CRITICO");
        assertThat(resposta.at("/alertas/0/tipoAlerta").asText()).isEqualTo("RESERVATORIO_CRITICO");
        assertThat(resposta.at("/alertas/0/dataHoraAlerta").asText()).isEqualTo(base.toString());
        assertThat(leituraRepository.count()).isEqualTo(1);
        assertThat(alertaRepository.count()).isEqualTo(1);
    }

    @Test
    void deveDeduplicarEResolverAlertasAoLongoDoCicloDoReservatorio() throws Exception {
        postar(corpoJson(0, "METALICA", 82.0, "ALERTA_ALTO"));
        postar(corpoJson(1, "METALICA", 84.0, "ALERTA_ALTO"));
        postar(corpoJson(2, "NAO_METALICA", 87.0, "ALERTA_ALTO"));

        mockMvc.perform(get("/api/v1/leituras/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipoAlerta").value("RESERVATORIO_ALTO"))
                .andExpect(jsonPath("$[0].dispositivoId").value("ESP32-001"));

        postar(corpoJson(3, "METALICA", 25.0, "NORMAL"));

        mockMvc.perform(get("/api/v1/leituras/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        assertThat(alertaRepository.count()).isEqualTo(1);
    }

    @Test
    void deveTratarIndutivoECapacitivoJuntosComoMetalicaSemAlerta() throws Exception {
        // o capacitivo também reage a metal: os dois sensores juntos não são conflito
        JsonNode resposta = postar(corpoJson(0, "AMBAS", 50.0, "NORMAL"));

        assertThat(resposta.at("/peca/tipo").asText()).isEqualTo("METALICA");
        mockMvc.perform(get("/api/v1/leituras/alertas"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void devePermitirResolucaoManualDoAlerta() throws Exception {
        JsonNode resposta = postar(corpoJson(0, "METALICA", 10.0, "ALERTA_BAIXO"));
        long alertaId = resposta.at("/alertas/0/id").asLong();

        mockMvc.perform(put("/api/v1/leituras/alertas/" + alertaId + "/resolver"))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/leituras/alertas/999999/resolver"))
                .andExpect(status().isNotFound());

        assertThat(alertaRepository.contarNaoResolvidos()).isZero();
    }

    @Test
    void deveCalcularEstatisticasSobreTodoOHistorico() throws Exception {
        postar(corpoJson(0, "METALICA", 30.0, "NORMAL"));
        postar(corpoJson(1, "METALICA", 40.0, "NORMAL"));
        postar(corpoJson(2, "NAO_METALICA", 50.0, "NORMAL"));
        postar(corpoJson(3, "AMBAS", 85.0, "ALERTA_ALTO"));

        mockMvc.perform(get("/api/v1/leituras/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeituras").value(4))
                .andExpect(jsonPath("$.totalMetalicas").value(3))
                .andExpect(jsonPath("$.percentualPorTipoPeca.METALICA").value(75.0))
                .andExpect(jsonPath("$.mediaNivelReservatorio").value(51.25))
                .andExpect(jsonPath("$.medianaNivelReservatorio").value(45.0))
                .andExpect(jsonPath("$.quartil1NivelReservatorio").value(37.5))
                .andExpect(jsonPath("$.quartil3NivelReservatorio").value(58.75))
                .andExpect(jsonPath("$.distribuicaoStatusReservatorio.ALERTA_ALTO.quantidade").value(1))
                .andExpect(jsonPath("$.alertasPorTipo.RESERVATORIO_ALTO").value(1))
                .andExpect(jsonPath("$.alertasPorTipo.ENCHIMENTO_RAPIDO").value(1))
                .andExpect(jsonPath("$.alertasAtivos").value(2));
    }

    @Test
    void deveAlertarEnchimentoRapidoEResolverQuandoATaxaNormaliza() throws Exception {
        postar(corpoJson(0, "METALICA", 30.0, "NORMAL"));
        postar(corpoJson(1, "METALICA", 40.0, "NORMAL")); // +10 %/min

        mockMvc.perform(get("/api/v1/leituras/alertas"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipoAlerta").value("ENCHIMENTO_RAPIDO"));

        postar(corpoJson(5, "METALICA", 40.5, "NORMAL")); // janela [2,5) vazia: sem base, alerta resolvido

        mockMvc.perform(get("/api/v1/leituras/alertas"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deveListarLeiturasPaginadas() throws Exception {
        postar(corpoJson(0, "METALICA", 30.0, "NORMAL"));
        postar(corpoJson(1, "NAO_METALICA", 31.0, "NORMAL"));

        mockMvc.perform(get("/api/v1/leituras").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(1)))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }
}
