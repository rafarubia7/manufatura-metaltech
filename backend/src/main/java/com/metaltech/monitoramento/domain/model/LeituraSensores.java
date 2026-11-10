package com.metaltech.monitoramento.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leituras_sensores", indexes = {
    @Index(name = "idx_timestamp", columnList = "timestamp_leitura"),
    @Index(name = "idx_tipo_peca", columnList = "tipo_peca"),
    @Index(name = "idx_status_reservatorio", columnList = "status_reservatorio"),
    @Index(name = "idx_dispositivo_id", columnList = "dispositivo_id")
})
@Getter
@Setter
@NoArgsConstructor
public class LeituraSensores {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp_leitura", nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "distancia_cm", nullable = false)
    private Double distanciaCm;

    @Column(name = "indutivo_detectou_metal", nullable = false)
    private Boolean indutivoDetectouMetal;

    @Column(name = "indutivo_intensidade")
    private Double indutivoIntensidade;

    @Column(name = "capacitivo_detectou_nao_metal", nullable = false)
    private Boolean capacitivoDetectouNaoMetal;

    @Column(name = "capacitivo_intensidade")
    private Double capacitivoIntensidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_peca", nullable = false)
    private TipoPeca tipoPeca;

    @Column(name = "nivel_reservatorio_percentual", nullable = false)
    private Double nivelReservatorioPercentual;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_reservatorio", nullable = false)
    private StatusReservatorio statusReservatorio;

    @Column(name = "dispositivo_id", nullable = false)
    private String dispositivoId;

    @Column(name = "versao_firmware")
    private String versaoFirmware;

    @OneToMany(mappedBy = "leitura", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlertaNivel> alertas = new ArrayList<>();

    public LeituraSensores(Long id,
                          Instant timestamp,
                          Double distanciaCm,
                          Boolean indutivoDetectouMetal,
                          Double indutivoIntensidade,
                          Boolean capacitivoDetectouNaoMetal,
                          Double capacitivoIntensidade,
                          TipoPeca tipoPeca,
                          Double nivelReservatorioPercentual,
                          StatusReservatorio statusReservatorio,
                          String dispositivoId,
                          String versaoFirmware,
                          List<AlertaNivel> alertas) {
        this.id = id;
        this.timestamp = timestamp;
        this.distanciaCm = distanciaCm;
        this.indutivoDetectouMetal = indutivoDetectouMetal;
        this.indutivoIntensidade = indutivoIntensidade;
        this.capacitivoDetectouNaoMetal = capacitivoDetectouNaoMetal;
        this.capacitivoIntensidade = capacitivoIntensidade;
        this.tipoPeca = tipoPeca;
        this.nivelReservatorioPercentual = nivelReservatorioPercentual;
        this.statusReservatorio = statusReservatorio;
        this.dispositivoId = dispositivoId;
        this.versaoFirmware = versaoFirmware;
        this.alertas = alertas == null ? new ArrayList<>() : new ArrayList<>(alertas);
    }

    public void setAlertas(List<AlertaNivel> alertas) {
        this.alertas = alertas == null ? new ArrayList<>() : new ArrayList<>(alertas);
    }

    public void adicionarAlerta(AlertaNivel alerta) {
        if (this.alertas == null) {
            this.alertas = new ArrayList<>();
        }
        this.alertas.add(alerta);
        alerta.setLeitura(this);
    }

    public void removerAlerta(AlertaNivel alerta) {
        if (this.alertas == null) {
            return;
        }
        this.alertas.remove(alerta);
        if (alerta != null) {
            alerta.setLeitura(null);
        }
    }
}