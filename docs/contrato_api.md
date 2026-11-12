# Contrato da API MetalTech (v1)

Base: `http://<host>:8080/api/v1`. JSON em UTF-8. Datas em ISO-8601 UTC com `Z`.
Documentação interativa: `/swagger-ui.html`.

> Itens marcados **PROPOSTA** precisam do aceite da Automação, do Front e da RA.

## 1. Semântica de uma leitura (PROPOSTA)

- **Uma leitura = uma peça** que passou pelo ponto de sensoriamento, mais o nível do reservatório naquele instante.
- O dispositivo envia o `POST` **quando detecta uma peça**. Amostras periódicas sem peça **não** devem ser enviadas: elas seriam contadas como `DESCONHECIDA` e distorceriam a contagem.
- O tipo da peça é **derivado dos sensores pelo back-end**:

| Indutivo acionado | Capacitivo acionado | `tipo` |
|---|---|---|
| sim | não | `METALICA` |
| não | sim | `NAO_METALICA` |
| sim | sim | `AMBAS` (conflito, gera alerta) |
| não | não | `DESCONHECIDA` |

- O `tipo` e o `status` enviados pelo dispositivo são **informativos**: o back-end recalcula e registra divergência no log.
- `percentual` (0 a 100) é calculado pelo dispositivo; o back-end confia nele.

## 2. Enviar leitura

`POST /leituras`. Corpo:

```json
{
  "timestamp": "2026-11-03T08:00:00Z",
  "sensorDistancia": { "valor": 9.5, "unidade": "cm", "status": "OK" },
  "sensorIndutivo": { "detectouMetal": true, "intensidade": 0.82, "status": "OK" },
  "sensorCapacitivo": { "detectouNaoMetal": false, "intensidade": 0.06, "status": "OK" },
  "peca": { "id": "peca-1", "tipo": "METALICA", "timestampDetectado": "2026-11-03T08:00:00Z" },
  "nivelReservatorio": { "nivelAtual": 97.0, "capacidadeMaxima": 100.0, "percentual": 97.0, "status": "CRITICO" },
  "dispositivoId": "ESP32-001",
  "versaoFirmware": "1.0.0"
}
```

Autenticação: quando o back-end tiver `API_KEY` configurada, este `POST` exige o cabeçalho `X-API-Key: <chave>`; sem ele, ou com chave errada, a resposta é `401` (`NAO_AUTORIZADO`). As consultas (`GET`) não exigem chave.

Resposta `201` com a leitura persistida (campo `id`) e a lista `alertas` gerados.

## 3. Endpoints

| Método | Rota | Uso |
|---|---|---|
| POST | `/leituras` | Registrar leitura (dispositivo) |
| GET | `/leituras` | Listar com paginação e filtros `inicio`, `fim`, `tipoPeca`, `statusReservatorio` |
| GET | `/leituras/{id}` | Buscar por id |
| PUT | `/leituras/{id}` | Corrigir leitura (o `timestamp` não muda) |
| DELETE | `/leituras/{id}` | Excluir leitura e seus alertas |
| GET | `/leituras/alertas` | Alertas não resolvidos |
| PUT | `/leituras/alertas/{id}/resolver` | Resolver alerta (operador) |
| GET | `/leituras/estatisticas` | Indicadores; aceita `inicio` e `fim` juntos |
| GET | `/esteira/status` | **Estado atual** para dashboard e RA |

## 4. Enums

- `TipoPeca`: `METALICA`, `NAO_METALICA`, `DESCONHECIDA`, `AMBAS`.
- `StatusReservatorio`: `NORMAL`, `ALERTA_BAIXO` (< 20%), `ALERTA_ALTO` (> 80%), `CRITICO` (>= 95%).
- `TipoAlerta`: `RESERVATORIO_BAIXO`, `RESERVATORIO_ALTO`, `RESERVATORIO_CRITICO`, `CONTAGEM_ANOMALA`, `ENCHIMENTO_RAPIDO`.

## 5. Critérios de alerta

- Nível: um alerta ativo por tipo e dispositivo; resolvido sozinho quando o status muda.
- Conflito de sensores (`AMBAS`): um alerta por ocorrência.
- Enchimento rápido: taxa acima de **5 %/min**, medida entre a leitura atual e a primeira leitura dos 3 minutos anteriores (mínimo de 1 minuto entre elas). Um alerta por episódio. Critério definido pela CIDA.

## 6. Erros

Todos os erros usam o mesmo formato:

```json
{
  "dataHora": "2026-11-03T08:00:00Z",
  "codigoErro": "ERRO_VALIDACAO",
  "mensagem": "Falha na validação dos dados de entrada",
  "caminho": "uri=/api/v1/leituras",
  "detalhes": ["peca.tipo: deve corresponder a ..."]
}
```

Códigos: `ERRO_VALIDACAO` (400), `REQUISICAO_INVALIDA` (400), `NAO_ENCONTRADO` (404), `NAO_AUTORIZADO` (401), `ERRO_INTERNO` (500).

## 7. Mudanças

Qualquer mudança que quebre este contrato é combinada com toda a equipe antes de entrar na `main`.
