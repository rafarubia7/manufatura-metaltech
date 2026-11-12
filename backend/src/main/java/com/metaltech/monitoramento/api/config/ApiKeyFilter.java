package com.metaltech.monitoramento.api.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Exige X-API-Key no POST de leituras (dispositivo). Sem chave configurada, o filtro fica desligado. */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    static final String CABECALHO = "X-API-Key";

    private final byte[] chaveEsperada;

    public ApiKeyFilter(@Value("${app.seguranca.api-key:}") String chave) {
        this.chaveEsperada = chave.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        boolean protegido = chaveEsperada.length > 0
                && "POST".equals(request.getMethod())
                && "/api/v1/leituras".equals(request.getRequestURI());
        return !protegido;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadeia)
            throws ServletException, IOException {
        String recebida = request.getHeader(CABECALHO);
        boolean valida = recebida != null
                && MessageDigest.isEqual(chaveEsperada, recebida.getBytes(StandardCharsets.UTF_8));
        if (!valida) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"codigoErro\":\"NAO_AUTORIZADO\",\"mensagem\":\"API key ausente ou inválida\"}");
            return;
        }
        cadeia.doFilter(request, response);
    }
}
