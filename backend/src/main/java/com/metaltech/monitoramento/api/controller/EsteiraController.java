package com.metaltech.monitoramento.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.metaltech.monitoramento.application.service.EsteiraStatus;
import com.metaltech.monitoramento.application.service.EsteiraStatusService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/esteira")
@RequiredArgsConstructor
@Tag(name = "Esteira", description = "Estado atual da esteira para o dashboard e a RA")
public class EsteiraController {

    private final EsteiraStatusService statusService;

    @GetMapping("/status")
    @Operation(summary = "Estado atual: último nível, contagens por tipo de peça e alertas ativos",
            description = "Retorna 404 enquanto não houver nenhuma leitura registrada.")
    public ResponseEntity<EsteiraStatus> obterStatus() {
        return ResponseEntity.ok(statusService.obterStatusAtual());
    }
}
