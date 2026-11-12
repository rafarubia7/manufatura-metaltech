-- Tabela de leituras de sensores
CREATE TABLE leituras_sensores (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    timestamp_leitura TIMESTAMP(6) NOT NULL,
    distancia_cm DOUBLE NOT NULL,
    indutivo_detectou_metal TINYINT(1) NOT NULL,
    indutivo_intensidade DOUBLE NULL,
    capacitivo_detectou_nao_metal TINYINT(1) NOT NULL,
    capacitivo_intensidade DOUBLE NULL,
    tipo_peca VARCHAR(20) NOT NULL,
    nivel_reservatorio_percentual DOUBLE NOT NULL,
    status_reservatorio VARCHAR(20) NOT NULL,
    dispositivo_id VARCHAR(50) NOT NULL,
    versao_firmware VARCHAR(20) NULL,
    criado_em TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atualizado_em TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

-- Tabela de alertas
CREATE TABLE alertas_nivel (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    leitura_id BIGINT NOT NULL,
    timestamp_alertado TIMESTAMP(6) NOT NULL,
    tipo_alerta VARCHAR(20) NOT NULL,
    mensagem TEXT NOT NULL,
    resolvido TINYINT(1) NOT NULL DEFAULT 0,
    criado_em TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_alertas_leitura FOREIGN KEY (leitura_id) REFERENCES leituras_sensores(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Índices para performance
CREATE INDEX idx_leituras_timestamp ON leituras_sensores(timestamp_leitura);
CREATE INDEX idx_leituras_tipo_peca ON leituras_sensores(tipo_peca);
CREATE INDEX idx_leituras_status_reservatorio ON leituras_sensores(status_reservatorio);
CREATE INDEX idx_leituras_dispositivo ON leituras_sensores(dispositivo_id);
CREATE INDEX idx_alertas_leitura ON alertas_nivel(leitura_id);
CREATE INDEX idx_alertas_tipo ON alertas_nivel(tipo_alerta);
CREATE INDEX idx_alertas_resolvido ON alertas_nivel(resolvido);
