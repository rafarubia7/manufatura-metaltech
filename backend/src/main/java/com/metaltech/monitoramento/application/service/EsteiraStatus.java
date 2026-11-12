package com.metaltech.monitoramento.application.service;

import java.time.Instant;

/** Estado atual da esteira: o que o dashboard e a RA precisam, sem paginar o histórico. */
public record EsteiraStatus(
        Instant ultimaLeitura,
        String dispositivoId,
        double nivelReservatorioPercentual,
        String statusReservatorio,
        long totalMetalicas,
        long totalNaoMetalicas,
        long totalDesconhecidas,
        long totalAmbas,
        long alertasAtivos) {
}
