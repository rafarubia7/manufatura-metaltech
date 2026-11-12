package com.metaltech.monitoramento.domain.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class StatusReservatorioConverter implements AttributeConverter<StatusReservatorio, String> {

    @Override
    public String convertToDatabaseColumn(StatusReservatorio attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public StatusReservatorio convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return StatusReservatorio.valueOf(dbData);
    }
}
