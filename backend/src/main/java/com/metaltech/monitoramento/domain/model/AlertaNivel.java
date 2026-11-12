package com.metaltech.monitoramento.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "alertas_nivel")
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
    private Instant dataHoraAlerta;

    @Convert(converter = TipoAlertaConverter.class)
    @Column(name = "tipo_alerta", nullable = false)
    private TipoAlerta tipoAlerta;

    @Column(name = "mensagem", nullable = false, length = 500)
    private String mensagem;

    @Column(name = "resolvido", nullable = false)
    private Boolean resolvido = false;

    public AlertaNivel(LeituraSensores leitura, Instant dataHoraAlerta, TipoAlerta tipoAlerta, String mensagem, Boolean resolvido) {
        this.leitura = leitura;
        this.dataHoraAlerta = dataHoraAlerta;
        this.tipoAlerta = tipoAlerta;
        this.mensagem = mensagem;
        this.resolvido = resolvido;
    }

    public void marcarComoResolvido() {
        this.resolvido = true;
    }
}
