-- Tabela de leituras de sensores
CREATE TABLE leituras_sensores (
    id BIGSERIAL PRIMARY KEY,
    timestamp_leitura TIMESTAMP WITH TIME ZONE NOT NULL,
    distancia_cm DOUBLE PRECISION NOT NULL,
    indutivo_detectou_metal BOOLEAN NOT NULL,
    indutivo_intensidade DOUBLE PRECISION,
    capacitivo_detectou_nao_metal BOOLEAN NOT NULL,
    capacitivo_intensidade DOUBLE PRECISION,
    tipo_peca VARCHAR(20) NOT NULL,
    nivel_reservatorio_percentual DOUBLE PRECISION NOT NULL,
    status_reservatorio VARCHAR(20) NOT NULL,
    dispositivo_id VARCHAR(50) NOT NULL,
    versao_firmware VARCHAR(20),
    criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de alertas
CREATE TABLE alertas_nivel (
    id BIGSERIAL PRIMARY KEY,
    leitura_id BIGINT NOT NULL REFERENCES leituras_sensores(id) ON DELETE CASCADE,
    timestamp_alertado TIMESTAMP WITH TIME ZONE NOT NULL,
    tipo_alerta VARCHAR(20) NOT NULL,
    mensagem TEXT NOT NULL,
    resolvido BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índices para performance
CREATE INDEX idx_leituras_timestamp ON leituras_sensores(timestamp_leitura);
CREATE INDEX idx_leituras_tipo_peca ON leituras_sensores(tipo_peca);
CREATE INDEX idx_leituras_status_reservatorio ON leituras_sensores(status_reservatorio);
CREATE INDEX idx_leituras_dispositivo ON leituras_sensores(dispositivo_id);
CREATE INDEX idx_alertas_leitura ON alertas_nivel(leitura_id);
CREATE INDEX idx_alertas_tipo ON alertas_nivel(tipo_alerta);
CREATE INDEX idx_alertas_resolvido ON alertas_nivel(resolvido);

-- Comentários para documentação
COMMENT ON TABLE leituras_sensores IS 'Armazena as leituras dos sensores da esteira de triagem';
COMMENT ON TABLE alertas_nivel IS 'Armazena os alertas gerados automaticamente';
