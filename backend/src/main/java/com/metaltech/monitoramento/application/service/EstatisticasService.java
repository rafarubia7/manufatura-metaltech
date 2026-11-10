package com.metaltech.monitoramento.application.service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoAlerta;
import com.metaltech.monitoramento.domain.model.TipoPeca;
import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

import lombok.RequiredArgsConstructor;

/**
 * Indicadores operacionais da esteira calculados sobre as leituras persistidas.
 * Os quartis usam interpolação linear e o desvio padrão é amostral (n - 1),
 * as mesmas convenções do pandas, para permitir a validação cruzada com a CIDA.
 */
@Service
@RequiredArgsConstructor
public class EstatisticasService {

    private final LeituraSensoresRepository leituraRepository;
    private final AlertaNivelRepository alertaRepository;

    /**
     * @param inicio início do período (inclusivo); nulo junto com {@code fim} = todo o histórico
     * @param fim    fim do período (inclusivo)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> calcularEstatisticas(Instant inicio, Instant fim) {
        boolean periodoInformado = inicio != null && fim != null;
        List<LeituraSensores> leituras = periodoInformado
                ? leituraRepository.findByTimestampBetween(inicio, fim)
                : leituraRepository.findAll(Sort.by("timestamp"));

        Map<TipoPeca, Long> porTipo = new EnumMap<>(TipoPeca.class);
        for (TipoPeca tipo : TipoPeca.values()) {
            porTipo.put(tipo, 0L);
        }
        Map<StatusReservatorio, Long> porStatus = new EnumMap<>(StatusReservatorio.class);
        for (StatusReservatorio status : StatusReservatorio.values()) {
            porStatus.put(status, 0L);
        }
        for (LeituraSensores l : leituras) {
            porTipo.merge(l.getTipoPeca(), 1L, Long::sum);
            porStatus.merge(l.getStatusReservatorio(), 1L, Long::sum);
        }

        List<Double> niveis = leituras.stream()
                .map(LeituraSensores::getNivelReservatorioPercentual)
                .sorted()
                .toList();

        int total = leituras.size();
        double media = niveis.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        Map<String, Object> estatisticas = new LinkedHashMap<>();
        estatisticas.put("periodoInicio", periodoInformado ? inicio : (total > 0 ? leituras.get(0).getTimestamp() : null));
        estatisticas.put("periodoFim", periodoInformado ? fim : (total > 0 ? leituras.get(total - 1).getTimestamp() : null));
        estatisticas.put("totalLeituras", total);
        estatisticas.put("totalMetalicas", porTipo.get(TipoPeca.METALICA));
        estatisticas.put("totalNaoMetalicas", porTipo.get(TipoPeca.NAO_METALICA));
        estatisticas.put("totalDesconhecidas", porTipo.get(TipoPeca.DESCONHECIDA));
        estatisticas.put("totalAmbas", porTipo.get(TipoPeca.AMBAS));
        estatisticas.put("percentualPorTipoPeca", percentuais(porTipo, total));
        estatisticas.put("taxaConflitoSensores", total == 0 ? 0.0 : arredondar(100.0 * porTipo.get(TipoPeca.AMBAS) / total));
        estatisticas.put("mediaNivelReservatorio", media);
        estatisticas.put("medianaNivelReservatorio", calcularPercentil(niveis, 50));
        estatisticas.put("desvioPadraoNivelReservatorio", calcularDesvioPadraoAmostral(niveis, media));
        estatisticas.put("quartil1NivelReservatorio", calcularPercentil(niveis, 25));
        estatisticas.put("quartil3NivelReservatorio", calcularPercentil(niveis, 75));
        estatisticas.put("minimoNivelReservatorio", niveis.isEmpty() ? 0.0 : niveis.get(0));
        estatisticas.put("maximoNivelReservatorio", niveis.isEmpty() ? 0.0 : niveis.get(niveis.size() - 1));
        estatisticas.put("distribuicaoStatusReservatorio", contagensComPercentual(porStatus, total));
        estatisticas.put("alertasAtivos", alertaRepository.countByResolvidoFalse());
        estatisticas.put("alertasPorTipo", contarAlertas(periodoInformado
                ? alertaRepository.contarPorTipoNoPeriodo(inicio, fim)
                : alertaRepository.contarPorTipo()));
        return estatisticas;
    }

    private <E extends Enum<E>> Map<String, Double> percentuais(Map<E, Long> contagens, int total) {
        Map<String, Double> resultado = new LinkedHashMap<>();
        contagens.forEach((chave, qtd) -> resultado.put(chave.name(), total == 0 ? 0.0 : arredondar(100.0 * qtd / total)));
        return resultado;
    }

    private <E extends Enum<E>> Map<String, Map<String, Object>> contagensComPercentual(Map<E, Long> contagens, int total) {
        Map<String, Map<String, Object>> resultado = new LinkedHashMap<>();
        contagens.forEach((chave, qtd) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("quantidade", qtd);
            item.put("percentual", total == 0 ? 0.0 : arredondar(100.0 * qtd / total));
            resultado.put(chave.name(), item);
        });
        return resultado;
    }

    private Map<String, Long> contarAlertas(List<Object[]> linhas) {
        Map<String, Long> resultado = new LinkedHashMap<>();
        for (TipoAlerta tipo : TipoAlerta.values()) {
            resultado.put(tipo.name(), 0L);
        }
        for (Object[] linha : linhas) {
            resultado.put(((TipoAlerta) linha[0]).name(), ((Number) linha[1]).longValue());
        }
        return resultado;
    }

    private double calcularDesvioPadraoAmostral(List<Double> valores, double media) {
        if (valores.size() < 2) {
            return 0.0;
        }
        double somaQuadrados = valores.stream()
                .mapToDouble(v -> Math.pow(v - media, 2))
                .sum();
        return Math.sqrt(somaQuadrados / (valores.size() - 1));
    }

    /** Percentil com interpolação linear sobre a lista já ordenada. */
    private double calcularPercentil(List<Double> valores, int percentual) {
        if (valores.isEmpty()) {
            return 0.0;
        }
        if (valores.size() == 1) {
            return valores.get(0);
        }
        double posicao = (percentual / 100.0) * (valores.size() - 1);
        int indiceBase = (int) Math.floor(posicao);
        double fracao = posicao - indiceBase;
        if (indiceBase >= valores.size() - 1) {
            return valores.get(valores.size() - 1);
        }
        double inferior = valores.get(indiceBase);
        double superior = valores.get(indiceBase + 1);
        return inferior + (superior - inferior) * fracao;
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
