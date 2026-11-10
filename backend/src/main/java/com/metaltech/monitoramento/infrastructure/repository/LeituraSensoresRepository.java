package com.metaltech.monitoramento.infrastructure.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoPeca;

public interface LeituraSensoresRepository extends JpaRepository<LeituraSensores, Long> {

    List<LeituraSensores> findByTimestampBetween(Instant inicio, Instant fim);

    List<LeituraSensores> findByTipoPeca(TipoPeca tipo);

    List<LeituraSensores> findByStatusReservatorio(StatusReservatorio status);

    Page<LeituraSensores> findAllByOrderByTimestampDesc(Pageable pageable);

    @Query("SELECT l FROM LeituraSensores l WHERE l.timestamp >= :inicio AND l.timestamp <= :fim AND l.tipoPeca = :tipo")
    List<LeituraSensores> findByTimestampAndTipoPeca(
        @Param("inicio") Instant inicio,
        @Param("fim") Instant fim,
        @Param("tipo") TipoPeca tipo
    );

    @Query("SELECT l FROM LeituraSensores l WHERE l.timestamp >= :inicio AND l.timestamp <= :fim AND l.statusReservatorio = :status")
    List<LeituraSensores> findByTimestampAndStatusReservatorio(
        @Param("inicio") Instant inicio,
        @Param("fim") Instant fim,
        @Param("status") StatusReservatorio status
    );

    @Query("SELECT l FROM LeituraSensores l WHERE l.dispositivoId = :dispositivoId ORDER BY l.timestamp DESC")
    List<LeituraSensores> findByDispositivoIdOrderByTimestampDesc(
        @Param("dispositivoId") String dispositivoId
    );

    // Novos métodos com Pageable para o Controller
    @Query("SELECT l FROM LeituraSensores l WHERE l.timestamp >= :inicio AND l.timestamp <= :fim AND l.tipoPeca = :tipo")
    Page<LeituraSensores> findByTimestampAndTipoPeca(
        @Param("inicio") Instant inicio,
        @Param("fim") Instant fim,
        @Param("tipo") TipoPeca tipo,
        Pageable pageable
    );

    @Query("SELECT l FROM LeituraSensores l WHERE l.timestamp >= :inicio AND l.timestamp <= :fim AND l.statusReservatorio = :status")
    Page<LeituraSensores> findByTimestampAndStatusReservatorio(
        @Param("inicio") Instant inicio,
        @Param("fim") Instant fim,
        @Param("status") StatusReservatorio status,
        Pageable pageable
    );

    @Query("SELECT l FROM LeituraSensores l WHERE l.timestamp >= :inicio AND l.timestamp <= :fim")
    Page<LeituraSensores> findByTimestampBetween(
        @Param("inicio") Instant inicio,
        @Param("fim") Instant fim,
        Pageable pageable
    );

    Page<LeituraSensores> findByTipoPeca(TipoPeca tipo, Pageable pageable);

    Page<LeituraSensores> findByStatusReservatorio(StatusReservatorio status, Pageable pageable);
}