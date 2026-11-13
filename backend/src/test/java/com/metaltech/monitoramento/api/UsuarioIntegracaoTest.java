package com.metaltech.monitoramento.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.metaltech.monitoramento.infrastructure.repository.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsuarioIntegracaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void limparBase() {
        usuarioRepository.deleteAll();
    }

    private String corpo(String nome, String email, String perfil, Boolean ativo) {
        return """
            {"nome": "%s", "email": "%s", "perfil": "%s", "ativo": %s}
            """.formatted(nome, email, perfil, ativo);
    }

    private long criar(String nome, String email, String perfil) throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(nome, email, perfil, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("id").asLong();
    }

    @Test
    void deveExecutarOCrudCompleto() throws Exception {
        long id = criar("Bruno Lima", "Bruno@MetalTech.com", "OPERADOR");

        mockMvc.perform(get("/api/v1/usuarios/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bruno@metaltech.com"))
                .andExpect(jsonPath("$.perfil").value("OPERADOR"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.podeResolverAlertas").value(true))
                .andExpect(jsonPath("$.podeGerenciarUsuarios").value(false));

        mockMvc.perform(put("/api/v1/usuarios/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Bruno Lima", "bruno@metaltech.com", "ADMINISTRADOR", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.podeGerenciarUsuarios").value(false));

        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(delete("/api/v1/usuarios/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/usuarios/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void deveRecusarEmailDuplicadoSemDiferenciarMaiusculas() throws Exception {
        criar("Carla", "carla@metaltech.com", "VISUALIZADOR");

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Outra Carla", "CARLA@metaltech.com", "OPERADOR", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigoErro").value("REQUISICAO_INVALIDA"));
    }

    @Test
    void deveRecusarAtualizarParaEmailDeOutroUsuario() throws Exception {
        criar("Carla", "carla@metaltech.com", "VISUALIZADOR");
        long id = criar("Davi", "davi@metaltech.com", "OPERADOR");

        mockMvc.perform(put("/api/v1/usuarios/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Davi", "carla@metaltech.com", "OPERADOR", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveValidarCamposObrigatorios() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("", "nao-e-email", "GERENTE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigoErro").value("ERRO_VALIDACAO"))
                .andExpect(jsonPath("$.detalhes", hasSize(3)));
    }

    @Test
    void deveRetornar404ParaUsuarioInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/999999")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/usuarios/999999")).andExpect(status().isNotFound());
    }
}
