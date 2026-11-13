package com.metaltech.monitoramento.api.dto;

import java.time.Instant;

import com.metaltech.monitoramento.domain.model.Usuario;

public record UsuarioRespostaDTO(
        Long id,
        String nome,
        String email,
        String perfil,
        boolean ativo,
        boolean podeResolverAlertas,
        boolean podeGerenciarUsuarios,
        Instant criadoEm) {

    public static UsuarioRespostaDTO de(Usuario usuario) {
        return new UsuarioRespostaDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil().name(),
                Boolean.TRUE.equals(usuario.getAtivo()),
                usuario.podeResolverAlertas(),
                usuario.podeGerenciarUsuarios(),
                usuario.getCriadoEm());
    }
}
