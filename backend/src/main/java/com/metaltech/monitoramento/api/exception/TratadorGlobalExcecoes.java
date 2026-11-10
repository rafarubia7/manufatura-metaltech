package com.metaltech.monitoramento.api.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RestControllerAdvice
public class TratadorGlobalExcecoes extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<RespostaErro> tratarNaoEncontrado(EntityNotFoundException ex, WebRequest requisicao) {
        return responder(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", ex.getMessage(), requisicao, List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RespostaErro> tratarViolacaoRestricao(ConstraintViolationException ex, WebRequest requisicao) {
        List<String> violacoes = ex.getConstraintViolations().stream()
                .map(violacao -> violacao.getPropertyPath() + ": " + violacao.getMessage())
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "ERRO_VALIDACAO", "Violações de validação", requisicao, violacoes);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RespostaErro> tratarArgumentoInvalido(IllegalArgumentException ex, WebRequest requisicao) {
        return responder(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", ex.getMessage(), requisicao, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespostaErro> tratarErroInesperado(Exception ex, WebRequest requisicao) {
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", ex.getMessage(), requisicao, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        List<String> erros = ex.getBindingResult().getAllErrors().stream()
                .map(this::mensagemDoErro)
                .toList();
        RespostaErro erro = new RespostaErro(Instant.now(), "ERRO_VALIDACAO",
                "Falha na validação dos dados de entrada", requisicao.getDescription(false), erros);
        return new ResponseEntity<>(erro, status);
    }

    private ResponseEntity<RespostaErro> responder(HttpStatus status, String codigo, String mensagem,
                                                   WebRequest requisicao, List<String> detalhes) {
        RespostaErro erro = new RespostaErro(Instant.now(), codigo, mensagem, requisicao.getDescription(false), detalhes);
        return new ResponseEntity<>(erro, status);
    }

    private String mensagemDoErro(ObjectError erro) {
        if (erro instanceof FieldError erroCampo) {
            return erroCampo.getField() + ": " + erro.getDefaultMessage();
        }
        return erro.getDefaultMessage();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RespostaErro {
        private Instant dataHora;
        private String codigoErro;
        private String mensagem;
        private String caminho;
        private List<String> detalhes;
    }
}
