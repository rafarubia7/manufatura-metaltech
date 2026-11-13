package com.metaltech.monitoramento.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.metaltech.monitoramento.domain.model.PerfilUsuario;
import com.metaltech.monitoramento.domain.model.Usuario;
import com.metaltech.monitoramento.infrastructure.repository.UsuarioRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarioRepository.findAllByOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + id));
    }

    @Transactional
    public Usuario criar(String nome, String email, PerfilUsuario perfil) {
        if (usuarioRepository.existsByEmail(Usuario.normalizarEmail(email))) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + email);
        }
        return usuarioRepository.save(new Usuario(nome, email, perfil));
    }

    @Transactional
    public Usuario atualizar(Long id, String nome, String email, PerfilUsuario perfil, Boolean ativo) {
        Usuario usuario = buscarPorId(id);
        if (usuarioRepository.existsByEmailAndIdNot(Usuario.normalizarEmail(email), id)) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + email);
        }
        usuario.atualizar(nome, email, perfil);
        if (Boolean.FALSE.equals(ativo)) {
            usuario.desativar();
        } else if (Boolean.TRUE.equals(ativo)) {
            usuario.ativar();
        }
        return usuario;
    }

    @Transactional
    public void excluir(Long id) {
        usuarioRepository.delete(buscarPorId(id));
    }
}
