package com.metaltech.monitoramento.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.metaltech.monitoramento.domain.model.LeituraSensores;
import com.metaltech.monitoramento.domain.model.TipoPeca;
import com.metaltech.monitoramento.infrastructure.repository.LeituraSensoresRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstatisticasService {

    private final LeituraSensoresRepository leituraRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> calcularEstatisticas(Instant inicio, Instant fim) {
        List<LeituraSensores> leituras = leituraRepository.findByTimestampBetween(inicio, fim);

        if (leituras.isEmpty()) {
            Map<String, Object> vazio = new LinkedHashMap<>();
            vazio.put("totalLeituras", 0);
            vazio.put("totalMetalicas", 0);
            vazio.put("totalNaoMetalicas", 0);
            vazio.put("totalDesconhecidas", 0);
            vazio.put("totalAmbas", 0);
            vazio.put("mediaNivelReservatorio", 0.0);
            vazio.put("medianaNivelReservatorio", 0.0);
            vazio.put("desvioPadraoNivelReservatorio", 0.0);
            vazio.put("quartil1NivelReservatorio", 0.0);
            vazio.put("quartil3NivelReservatorio", 0.0);
            vazio.put("alertasAtivos", 0);
            return vazio;
        }

        long totalMetalicas = leituras.stream()
                .filter(l -> l.getTipoPeca() == TipoPeca.METALICA)
                .count();

        long totalNaoMetalicas = leituras.stream()
                .filter(l -> l.getTipoPeca() == TipoPeca.NAO_METALICA)
                .count();

        long totalDesconhecidas = leituras.stream()
                .filter(l -> l.getTipoPeca() == TipoPeca.DESCONHECIDA)
                .count();

        long totalAmbas = leituras.stream()
                .filter(l -> l.getTipoPeca() == TipoPeca.AMBAS)
                .count();

        List<Double> niveis = leituras.stream()
                .map(LeituraSensores::getNivelReservatorioPercentual)
                .sorted()
                .toList();

        double media = niveis.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double mediana = calcularMediana(niveis);
        double desvioPadrao = calcularDesvioPadrao(niveis, media);
        double quartil1 = calcularPercentil(niveis, 25);
        double quartil3 = calcularPercentil(niveis, 75);

        long alertasAtivos = leituras.stream()
                .flatMap(l -> l.getAlertas().stream())
                .filter(a -> a != null && !Boolean.TRUE.equals(a.getResolvido()))
                .count();

        Map<String, Object> estatisticas = new LinkedHashMap<>();
        estatisticas.put("totalLeituras", leituras.size());
        estatisticas.put("totalMetalicas", totalMetalicas);
        estatisticas.put("totalNaoMetalicas", totalNaoMetalicas);
        estatisticas.put("totalDesconhecidas", totalDesconhecidas);
        estatisticas.put("totalAmbas", totalAmbas);
        estatisticas.put("mediaNivelReservatorio", media);
        estatisticas.put("medianaNivelReservatorio", mediana);
        estatisticas.put("desvioPadraoNivelReservatorio", desvioPadrao);
        estatisticas.put("quartil1NivelReservatorio", quartil1);
        estatisticas.put("quartil3NivelReservatorio", quartil3);
        estatisticas.put("alertasAtivos", alertasAtivos);
        return estatisticas;
    }

    private double calcularMediana(List<Double> valores) {
        if (valores.isEmpty()) {
            return 0.0;
        }

        int tamanho = valores.size();
        int meio = tamanho / 2;

        if (tamanho % 2 == 0) {
            return (valores.get(meio - 1) + valores.get(meio)) / 2.0;
        }

        return valores.get(meio);
    }

    private double calcularDesvioPadrao(List<Double> valores, double media) {
        if (valores.isEmpty()) {
            return 0.0;
        }

        double variancia = valores.stream()
                .mapToDouble(v -> Math.pow(v - media, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variancia);
    }

    private double calcularPercentil(List<Double> valores, int percentual) {
        if (valores.isEmpty()) {
            return 0.0;
        }

        if (valores.size() == 1) {
            return valores.get(0);
        }

        double posicao = ((double) percentual / 100.0) * (valores.size() - 1);
        int indiceBase = (int) Math.floor(posicao);
        double frac = posicao - indiceBase;

        if (indiceBase >= valores.size() - 1) {
            return valores.get(valores.size() - 1);
        }

        double inferior = valores.get(indiceBase);
        double superior = valores.get(indiceBase + 1);
        return inferior + (superior - inferior) * frac;
    }
}