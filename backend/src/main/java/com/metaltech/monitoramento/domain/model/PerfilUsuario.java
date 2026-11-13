package com.metaltech.monitoramento.domain.model;

/** Papel do usuário na operação da esteira. Cada perfil define o que pode fazer. */
public enum PerfilUsuario {

    /** Gerencia usuários e acompanha toda a operação. */
    ADMINISTRADOR(true, true),
    /** Opera a esteira e trata as ocorrências. */
    OPERADOR(true, false),
    /** Só consulta o dashboard e a RA. */
    VISUALIZADOR(false, false);

    private final boolean resolveAlertas;
    private final boolean gerenciaUsuarios;

    PerfilUsuario(boolean resolveAlertas, boolean gerenciaUsuarios) {
        this.resolveAlertas = resolveAlertas;
        this.gerenciaUsuarios = gerenciaUsuarios;
    }

    public boolean podeResolverAlertas() {
        return resolveAlertas;
    }

    public boolean podeGerenciarUsuarios() {
        return gerenciaUsuarios;
    }
}
