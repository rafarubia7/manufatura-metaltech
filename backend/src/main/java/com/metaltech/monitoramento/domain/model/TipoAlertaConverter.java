package com.metaltech.monitoramento.domain.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TipoAlertaConverter implements AttributeConverter<TipoAlerta, String> {

    @Override
    public String convertToDatabaseColumn(TipoAlerta attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TipoAlerta convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return TipoAlerta.valueOf(dbData);
    }
}
