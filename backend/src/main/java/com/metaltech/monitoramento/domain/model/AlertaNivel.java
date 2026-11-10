package com.metaltech.monitoramento.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "alertas_nivel", indexes = {
    @Index(name = "idx_leitura_id", columnList = "leitura_id"),
    @Index(name = "idx_tipo_alerta", columnList = "tipo_alerta"),
    @Index(name = "idx_resolvido", columnList = "resolvido")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertaNivel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leitura_id", nullable = false)
    private LeituraSensores leitura;

    @Column(name = "timestamp_alertado", nullable = false, updatable = false)
    private Instant timestampAlertado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_alerta", nullable = false)
    private TipoAlerta tipoAlerta;

    @Column(name = "mensagem", nullable = false, length = 500)
    private String mensagem;

    @Column(name = "resolvido", nullable = false)
    private Boolean resolvido = false;

    // Métodos de conveniência
    public void marcarComoResolvido() {
        this.resolvido = true;
    }

    // Construtor para criação de alertas no service
    public AlertaNivel(LeituraSensores leitura, Instant timestampAlertado, TipoAlerta tipoAlerta, String mensagem, Boolean resolvido) {
        this.leitura = leitura;
        this.timestampAlertado = timestampAlertado;
        this.tipoAlerta = tipoAlerta;
        this.mensagem = mensagem;
        this.resolvido = resolvido;
    }
}