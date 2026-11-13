package com.metaltech.monitoramento.domain.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PerfilUsuarioConverter implements AttributeConverter<PerfilUsuario, String> {

    @Override
    public String convertToDatabaseColumn(PerfilUsuario attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public PerfilUsuario convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return PerfilUsuario.valueOf(dbData);
    }
}
