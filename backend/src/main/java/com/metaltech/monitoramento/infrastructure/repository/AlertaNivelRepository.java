package com.metaltech.monitoramento.infrastructure.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.metaltech.monitoramento.domain.model.AlertaNivel;
import com.metaltech.monitoramento.domain.model.TipoAlerta;

public interface AlertaNivelRepository extends JpaRepository<AlertaNivel, Long> {

    @Query("SELECT a FROM AlertaNivel a JOIN FETCH a.leitura WHERE a.resolvido = false ORDER BY a.dataHoraAlerta DESC")
    List<AlertaNivel> buscarNaoResolvidos();

    @Query("SELECT COUNT(a) FROM AlertaNivel a WHERE a.resolvido = false")
    long contarNaoResolvidos();

    @Query("SELECT DISTINCT a.tipoAlerta FROM AlertaNivel a WHERE a.resolvido = false")
    List<TipoAlerta> listarTiposNaoResolvidos();

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AlertaNivel a "
            + "WHERE a.tipoAlerta = :tipo AND a.resolvido = false AND a.leitura.dispositivoId = :dispositivoId")
    boolean existeAtivo(@Param("tipo") TipoAlerta tipo, @Param("dispositivoId") String dispositivoId);

    @Query("SELECT a FROM AlertaNivel a "
            + "WHERE a.tipoAlerta IN :tipos AND a.resolvido = false AND a.leitura.dispositivoId = :dispositivoId")
    List<AlertaNivel> buscarAtivosPorTipos(@Param("tipos") Collection<TipoAlerta> tipos, @Param("dispositivoId") String dispositivoId);

    @Query("SELECT a.tipoAlerta, COUNT(a) FROM AlertaNivel a GROUP BY a.tipoAlerta")
    List<Object[]> contarPorTipo();

    @Query("SELECT a.tipoAlerta, COUNT(a) FROM AlertaNivel a JOIN a.leitura l "
            + "WHERE l.dataHora BETWEEN :inicio AND :fim GROUP BY a.tipoAlerta")
    List<Object[]> contarPorTipoNoPeriodo(@Param("inicio") Instant inicio, @Param("fim") Instant fim);
}
