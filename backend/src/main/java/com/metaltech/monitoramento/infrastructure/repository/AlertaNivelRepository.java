package com.metaltech.monitoramento.infrastructure.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.metaltech.monitoramento.domain.model.AlertaNivel;
import com.metaltech.monitoramento.domain.model.TipoAlerta;

public interface AlertaNivelRepository extends JpaRepository<AlertaNivel, Long> {

    @Query("SELECT a FROM AlertaNivel a JOIN FETCH a.leitura WHERE a.resolvido = false ORDER BY a.timestampAlertado DESC")
    List<AlertaNivel> findByResolvidoFalse();

    List<AlertaNivel> findByTipoAlerta(TipoAlerta tipo);

    long countByResolvidoFalse();

    boolean existsByTipoAlertaAndResolvidoFalseAndLeitura_DispositivoId(TipoAlerta tipo, String dispositivoId);

    List<AlertaNivel> findByTipoAlertaInAndResolvidoFalseAndLeitura_DispositivoId(Collection<TipoAlerta> tipos, String dispositivoId);

    @Query("SELECT a.tipoAlerta, COUNT(a) FROM AlertaNivel a GROUP BY a.tipoAlerta")
    List<Object[]> contarPorTipo();

    @Query("SELECT a.tipoAlerta, COUNT(a) FROM AlertaNivel a JOIN a.leitura l "
            + "WHERE l.timestamp >= :inicio AND l.timestamp <= :fim GROUP BY a.tipoAlerta")
    List<Object[]> contarPorTipoNoPeriodo(@Param("inicio") java.time.Instant inicio, @Param("fim") java.time.Instant fim);
}
