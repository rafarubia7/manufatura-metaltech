package com.metaltech.monitoramento.domain.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TipoPecaConverter implements AttributeConverter<TipoPeca, String> {

    @Override
    public String convertToDatabaseColumn(TipoPeca attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TipoPeca convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return TipoPeca.valueOf(dbData);
    }
}
