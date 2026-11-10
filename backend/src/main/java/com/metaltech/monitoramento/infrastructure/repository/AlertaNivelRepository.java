package com.metaltech.monitoramento.infrastructure.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.metaltech.monitoramento.domain.model.AlertaNivel;

public interface AlertaNivelRepository extends JpaRepository<AlertaNivel, Long> {
    List<AlertaNivel> findByResolvidoFalse();
    List<AlertaNivel> findByTipoAlerta(com.metaltech.monitoramento.domain.model.TipoAlerta tipo);
}