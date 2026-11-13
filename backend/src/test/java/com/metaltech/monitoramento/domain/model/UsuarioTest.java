package com.metaltech.monitoramento.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsuarioTest {

    @Test
    void deveNormalizarEmailENome() {
        Usuario usuario = new Usuario("  Ana Souza ", " Ana.Souza@MetalTech.com ", PerfilUsuario.OPERADOR);

        assertThat(usuario.getNome()).isEqualTo("Ana Souza");
        assertThat(usuario.getEmail()).isEqualTo("ana.souza@metaltech.com");
        assertThat(usuario.getAtivo()).isTrue();
    }

    @Test
    void permissoesDependemDoPerfil() {
        Usuario admin = new Usuario("Admin", "admin@metaltech.com", PerfilUsuario.ADMINISTRADOR);
        Usuario operador = new Usuario("Op", "op@metaltech.com", PerfilUsuario.OPERADOR);
        Usuario visualizador = new Usuario("Vis", "vis@metaltech.com", PerfilUsuario.VISUALIZADOR);

        assertThat(admin.podeGerenciarUsuarios()).isTrue();
        assertThat(admin.podeResolverAlertas()).isTrue();
        assertThat(operador.podeGerenciarUsuarios()).isFalse();
        assertThat(operador.podeResolverAlertas()).isTrue();
        assertThat(visualizador.podeResolverAlertas()).isFalse();
    }

    @Test
    void usuarioDesativadoPerdeAsPermissoes() {
        Usuario admin = new Usuario("Admin", "admin@metaltech.com", PerfilUsuario.ADMINISTRADOR);
        admin.desativar();

        assertThat(admin.podeGerenciarUsuarios()).isFalse();
        assertThat(admin.podeResolverAlertas()).isFalse();

        admin.ativar();
        assertThat(admin.podeGerenciarUsuarios()).isTrue();
    }

    @Test
    void naoAceitaPerfilNulo() {
        assertThatThrownBy(() -> new Usuario("Sem perfil", "x@metaltech.com", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
