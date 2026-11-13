package com.metaltech.monitoramento.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.metaltech.monitoramento.api.dto.UsuarioDTO;
import com.metaltech.monitoramento.api.dto.UsuarioRespostaDTO;
import com.metaltech.monitoramento.application.service.UsuarioService;
import com.metaltech.monitoramento.domain.model.PerfilUsuario;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Cadastro de operadores, técnicos e administradores da plataforma")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Lista os usuários em ordem alfabética")
    public List<UsuarioRespostaDTO> listar() {
        return usuarioService.listar().stream().map(UsuarioRespostaDTO::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um usuário pelo id")
    public UsuarioRespostaDTO buscarPorId(@PathVariable("id") Long id) {
        return UsuarioRespostaDTO.de(usuarioService.buscarPorId(id));
    }

    @PostMapping
    @Operation(summary = "Cadastra um usuário", description = "O e-mail é único (sem diferenciar maiúsculas).")
    public ResponseEntity<UsuarioRespostaDTO> criar(@Valid @RequestBody UsuarioDTO dto) {
        var criado = usuarioService.criar(dto.nome(), dto.email(), PerfilUsuario.valueOf(dto.perfil()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioRespostaDTO.de(criado));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza nome, e-mail, perfil e, opcionalmente, ativa/desativa o usuário")
    public UsuarioRespostaDTO atualizar(@PathVariable("id") Long id, @Valid @RequestBody UsuarioDTO dto) {
        return UsuarioRespostaDTO.de(usuarioService.atualizar(
                id, dto.nome(), dto.email(), PerfilUsuario.valueOf(dto.perfil()), dto.ativo()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um usuário")
    public ResponseEntity<Void> excluir(@PathVariable("id") Long id) {
        usuarioService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
