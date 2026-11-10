package com.metaltech.monitoramento.application.service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.StatusReservatorio;
import com.metaltech.monitoramento.domain.model.TipoAlerta;
import com.metaltech.monitoramento.domain.model.TipoPeca;
import com.metaltech.monitoramento.infrastructure.repository.AlertaNivelRepository;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

import lombok.RequiredArgsConstructor;

// Quartis com interpolação linear e desvio padrão amostral: mesmas convenções do pandas (validação com a CIDA).
@Service
@RequiredArgsConstructor
public class EstatisticasService {

    private final LeituraSensoresRepository leituraRepository;
    private final AlertaNivelRepository alertaRepository;

    // Sem início e fim, considera todo o histórico.
    @Transactional(readOnly = true)
    public Map<String, Object> calcularEstatisticas(Instant inicio, Instant fim) {
        boolean periodoInformado = inicio != null && fim != null;
        List<LeituraSensores> leituras = periodoInformado
                ? leituraRepository.buscarPorPeriodo(inicio, fim)
                : leituraRepository.listarEmOrdemCronologica();

        Map<TipoPeca, Long> porTipo = new EnumMap<>(TipoPeca.class);
        for (TipoPeca tipo : TipoPeca.values()) {
            porTipo.put(tipo, 0L);
        }
        Map<StatusReservatorio, Long> porStatus = new EnumMap<>(StatusReservatorio.class);
        for (StatusReservatorio status : StatusReservatorio.values()) {
            porStatus.put(status, 0L);
        }
        for (LeituraSensores leitura : leituras) {
            porTipo.merge(leitura.getTipoPeca(), 1L, Long::sum);
            porStatus.merge(leitura.getStatusReservatorio(), 1L, Long::sum);
        }

        List<Double> niveis = leituras.stream()
                .map(LeituraSensores::getNivelReservatorioPercentual)
                .sorted()
                .toList();

        int total = leituras.size();
        double media = niveis.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        Map<String, Object> estatisticas = new LinkedHashMap<>();
        estatisticas.put("periodoInicio", periodoInformado ? inicio : (total > 0 ? leituras.get(0).getDataHora() : null));
        estatisticas.put("periodoFim", periodoInformado ? fim : (total > 0 ? leituras.get(total - 1).getDataHora() : null));
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
        estatisticas.put("alertasAtivos", alertaRepository.contarNaoResolvidos());
        estatisticas.put("alertasPorTipo", contarAlertas(periodoInformado
                ? alertaRepository.contarPorTipoNoPeriodo(inicio, fim)
                : alertaRepository.contarPorTipo()));
        return estatisticas;
    }

    private <E extends Enum<E>> Map<String, Double> percentuais(Map<E, Long> contagens, int total) {
        Map<String, Double> resultado = new LinkedHashMap<>();
        contagens.forEach((chave, quantidade) -> resultado.put(chave.name(), total == 0 ? 0.0 : arredondar(100.0 * quantidade / total)));
        return resultado;
    }

    private <E extends Enum<E>> Map<String, Map<String, Object>> contagensComPercentual(Map<E, Long> contagens, int total) {
        Map<String, Map<String, Object>> resultado = new LinkedHashMap<>();
        contagens.forEach((chave, quantidade) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("quantidade", quantidade);
            item.put("percentual", total == 0 ? 0.0 : arredondar(100.0 * quantidade / total));
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
                .mapToDouble(valor -> Math.pow(valor - media, 2))
                .sum();
        return Math.sqrt(somaQuadrados / (valores.size() - 1));
    }

    private double calcularPercentil(List<Double> valoresOrdenados, int percentual) {
        if (valoresOrdenados.isEmpty()) {
            return 0.0;
        }
        if (valoresOrdenados.size() == 1) {
            return valoresOrdenados.get(0);
        }
        double posicao = (percentual / 100.0) * (valoresOrdenados.size() - 1);
        int indiceBase = (int) Math.floor(posicao);
        double fracao = posicao - indiceBase;
        if (indiceBase >= valoresOrdenados.size() - 1) {
            return valoresOrdenados.get(valoresOrdenados.size() - 1);
        }
        double inferior = valoresOrdenados.get(indiceBase);
        double superior = valoresOrdenados.get(indiceBase + 1);
        return inferior + (superior - inferior) * fracao;
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
