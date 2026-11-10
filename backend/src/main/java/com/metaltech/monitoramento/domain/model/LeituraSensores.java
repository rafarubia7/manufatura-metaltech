package com.metaltech.monitoramento.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leituras_sensores")
@Getter
@Setter
@NoArgsConstructor
public class LeituraSensores {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp_leitura", nullable = false, updatable = false)
    private Instant dataHora;

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
                           Instant dataHora,
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
        this.dataHora = dataHora;
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
        setAlertas(alertas);
    }

    public void setAlertas(List<AlertaNivel> alertas) {
        this.alertas = alertas == null ? new ArrayList<>() : new ArrayList<>(alertas);
    }

    public void adicionarAlerta(AlertaNivel alerta) {
        alertas.add(alerta);
        alerta.setLeitura(this);
    }

    public void removerAlerta(AlertaNivel alerta) {
        if (alerta != null && alertas.remove(alerta)) {
            alerta.setLeitura(null);
        }
    }
}
