package com.metaltech.monitoramento.infrastructure.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoPeca;

public interface LeituraSensoresRepository extends JpaRepository<LeituraSensores, Long> {

    @Query("SELECT l FROM LeituraSensores l ORDER BY l.dataHora")
    List<LeituraSensores> listarEmOrdemCronologica();

    @Query("SELECT l FROM LeituraSensores l WHERE l.dataHora BETWEEN :inicio AND :fim ORDER BY l.dataHora")
    List<LeituraSensores> buscarPorPeriodo(@Param("inicio") Instant inicio, @Param("fim") Instant fim);

    @Query("SELECT l FROM LeituraSensores l WHERE l.tipoPeca = :tipo")
    List<LeituraSensores> buscarPorTipoPeca(@Param("tipo") TipoPeca tipo);

    @Query("SELECT l FROM LeituraSensores l WHERE l.statusReservatorio = :status")
    List<LeituraSensores> buscarPorStatusReservatorio(@Param("status") StatusReservatorio status);

    @Query("SELECT l FROM LeituraSensores l ORDER BY l.dataHora DESC")
    Page<LeituraSensores> listarMaisRecentes(Pageable paginacao);

    @Query("SELECT l FROM LeituraSensores l WHERE l.dataHora BETWEEN :inicio AND :fim")
    Page<LeituraSensores> buscarPorPeriodo(@Param("inicio") Instant inicio, @Param("fim") Instant fim, Pageable paginacao);

    @Query("SELECT l FROM LeituraSensores l WHERE l.dataHora BETWEEN :inicio AND :fim AND l.tipoPeca = :tipo")
    Page<LeituraSensores> buscarPorPeriodoETipoPeca(@Param("inicio") Instant inicio, @Param("fim") Instant fim,
                                                    @Param("tipo") TipoPeca tipo, Pageable paginacao);

    @Query("SELECT l FROM LeituraSensores l WHERE l.dataHora BETWEEN :inicio AND :fim AND l.statusReservatorio = :status")
    Page<LeituraSensores> buscarPorPeriodoEStatusReservatorio(@Param("inicio") Instant inicio, @Param("fim") Instant fim,
                                                              @Param("status") StatusReservatorio status, Pageable paginacao);

    @Query("SELECT l FROM LeituraSensores l WHERE l.tipoPeca = :tipo")
    Page<LeituraSensores> buscarPorTipoPeca(@Param("tipo") TipoPeca tipo, Pageable paginacao);

    @Query("SELECT l FROM LeituraSensores l WHERE l.statusReservatorio = :status")
    Page<LeituraSensores> buscarPorStatusReservatorio(@Param("status") StatusReservatorio status, Pageable paginacao);
}
