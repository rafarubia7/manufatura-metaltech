package com.metaltech.monitoramento.domain.model;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Usuário da plataforma. Os campos só mudam pelos métodos de domínio, que normalizam o e-mail
 * e impedem estados inválidos (encapsulamento).
 */
@Entity
@Table(name = "usuarios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    @Convert(converter = PerfilUsuarioConverter.class)
    @Column(name = "perfil", nullable = false, length = 20)
    private PerfilUsuario perfil;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public Usuario(String nome, String email, PerfilUsuario perfil) {
        atualizar(nome, email, perfil);
    }

    public void atualizar(String nome, String email, PerfilUsuario perfil) {
        if (perfil == null) {
            throw new IllegalArgumentException("Perfil do usuário é obrigatório");
        }
        this.nome = nome.trim();
        this.email = normalizarEmail(email);
        this.perfil = perfil;
    }

    public void ativar() {
        this.ativo = true;
    }

    public void desativar() {
        this.ativo = false;
    }

    public boolean podeResolverAlertas() {
        return Boolean.TRUE.equals(ativo) && perfil.podeResolverAlertas();
    }

    public boolean podeGerenciarUsuarios() {
        return Boolean.TRUE.equals(ativo) && perfil.podeGerenciarUsuarios();
    }

    public static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }
}
