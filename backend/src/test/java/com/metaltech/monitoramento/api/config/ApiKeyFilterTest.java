package com.metaltech.monitoramento.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyFilterTest {

    private MockFilterChain executar(ApiKeyFilter filtro, String metodo, String uri, String chave,
                                     MockHttpServletResponse resposta) throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest(metodo, uri);
        requisicao.setRequestURI(uri);
        if (chave != null) {
            requisicao.addHeader(ApiKeyFilter.CABECALHO, chave);
        }
        MockFilterChain cadeia = new MockFilterChain();
        filtro.doFilter(requisicao, resposta, cadeia);
        return cadeia;
    }

    @Test
    void deveBloquearPostSemChave() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockFilterChain cadeia = executar(new ApiKeyFilter("segredo"), "POST", "/api/v1/leituras", null, resposta);

        assertThat(resposta.getStatus()).isEqualTo(401);
        assertThat(cadeia.getRequest()).isNull();
    }

    @Test
    void deveBloquearPostComChaveErrada() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        executar(new ApiKeyFilter("segredo"), "POST", "/api/v1/leituras", "outra", resposta);

        assertThat(resposta.getStatus()).isEqualTo(401);
    }

    @Test
    void devePermitirPostComChaveCorreta() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockFilterChain cadeia = executar(new ApiKeyFilter("segredo"), "POST", "/api/v1/leituras", "segredo", resposta);

        assertThat(resposta.getStatus()).isEqualTo(200);
        assertThat(cadeia.getRequest()).isNotNull();
    }

    @Test
    void naoDeveProtegerConsultas() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockFilterChain cadeia = executar(new ApiKeyFilter("segredo"), "GET", "/api/v1/leituras", null, resposta);

        assertThat(cadeia.getRequest()).isNotNull();
    }

    @Test
    void deveFicarDesligadoQuandoNaoHaChaveConfigurada() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockFilterChain cadeia = executar(new ApiKeyFilter(""), "POST", "/api/v1/leituras", null, resposta);

        assertThat(cadeia.getRequest()).isNotNull();
    }
}
