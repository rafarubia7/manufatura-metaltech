package com.metaltech.monitoramento.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Corpo de criação/atualização de usuário. `ativo` só é usado na atualização (nulo = não muda). */
public record UsuarioDTO(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotNull @Pattern(regexp = "ADMINISTRADOR|OPERADOR|VISUALIZADOR") String perfil,
        Boolean ativo) {
}
