package com.metaltech.monitoramento.application.service;

import java.time.Instant;
import java.util.List;

/**
 * Estado atual da esteira: o que o dashboard e a RA precisam, sem paginar o histórico.
 *
 * @param tiposAlertaAtivos tipos dos alertas ainda não resolvidos (ex.: ENCHIMENTO_RAPIDO)
 * @param exibirAlerta      true quando o reservatório está perto da capacidade (alto/crítico) ou
 *                          enchendo fora do padrão da Ciência de Dados (enchimento rápido): a RA
 *                          usa este campo para o indicativo visual
 */
public record EsteiraStatus(
        Instant ultimaLeitura,
        String dispositivoId,
        double nivelReservatorioPercentual,
        String statusReservatorio,
        long totalMetalicas,
        long totalNaoMetalicas,
        long totalDesconhecidas,
        long totalAmbas,
        long alertasAtivos,
        List<String> tiposAlertaAtivos,
        boolean exibirAlerta) {
}
