-- 1) Mensagens de alerta gravadas com acentuação corrompida ("ReservatÃ³rio", "peÃ§a").
--    Causa: o código foi compilado no Windows sem project.build.sourceEncoding=UTF-8, então o javac
--    leu os fontes em cp1252. Desfaz a dupla codificação (latin1 do MySQL = cp1252).
--    Só altera linhas que contêm a sequência de bytes C3 83 ("Ã"), comparada em binário para não
--    pegar texto correto (a collation padrão ignora acentos).
UPDATE alertas_nivel
SET mensagem = CONVERT(CAST(CONVERT(mensagem USING latin1) AS BINARY) USING utf8mb4)
WHERE LOCATE(UNHEX('C383'), CAST(mensagem AS BINARY)) > 0;

-- 2) Nova regra de classificação: o capacitivo também reage a metal, então indutivo + capacitivo
--    é peça metálica, não conflito. As leituras antigas classificadas como AMBAS passam a METALICA
--    e os alertas de conflito delas são removidos (mesmo efeito de reprocessar a leitura pelo PUT).
DELETE a FROM alertas_nivel a
JOIN leituras_sensores l ON l.id = a.leitura_id
WHERE l.tipo_peca = 'AMBAS'
  AND l.indutivo_detectou_metal = 1
  AND a.tipo_alerta = 'CONTAGEM_ANOMALA';

UPDATE leituras_sensores
SET tipo_peca = 'METALICA'
WHERE tipo_peca = 'AMBAS'
  AND indutivo_detectou_metal = 1;
