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
| sim | sim | `METALICA` (o capacitivo também reage a metal; o indutivo decide) |
| não | não | `DESCONHECIDA` |

- `AMBAS` continua no enum só por compatibilidade com dados antigos: o back-end não gera mais esse tipo (a migration `V2` reclassificou as leituras antigas).

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
| GET | `/usuarios` | Listar usuários (ordem alfabética) |
| GET | `/usuarios/{id}` | Buscar usuário |
| POST | `/usuarios` | Cadastrar usuário |
| PUT | `/usuarios/{id}` | Atualizar usuário (e ativar/desativar) |
| DELETE | `/usuarios/{id}` | Excluir usuário |

Ordenação de `GET /leituras`: **sem filtros**, a lista vem sempre da leitura mais recente para a mais antiga (o dashboard e a CIDA dependem disso), e o parâmetro `sort` é ignorado. **Com filtros** (`inicio`/`fim`, `tipoPeca` ou `statusReservatorio`), vale o `sort` informado, e o padrão é `dataHora` crescente.

Corpo de `POST`/`PUT /usuarios`:

```json
{ "nome": "Ana Souza", "email": "ana@metaltech.com", "perfil": "OPERADOR", "ativo": true }
```

- O `email` é único e gravado em minúsculas. Repetido → 400 `REQUISICAO_INVALIDA`.
- O `ativo` é opcional e só vale no `PUT` (nulo = não muda).
- A resposta inclui `id`, `criadoEm` e as permissões derivadas do perfil (`podeResolverAlertas`, `podeGerenciarUsuarios`). Usuário inativo perde as permissões.

## 4. Enums

- `TipoPeca`: `METALICA`, `NAO_METALICA`, `DESCONHECIDA`, `AMBAS`.
- `StatusReservatorio`: `NORMAL`, `ALERTA_BAIXO` (< 20%), `ALERTA_ALTO` (> 80%), `CRITICO` (>= 95%).
- `TipoAlerta`: `RESERVATORIO_BAIXO`, `RESERVATORIO_ALTO`, `RESERVATORIO_CRITICO`, `CONTAGEM_ANOMALA`, `ENCHIMENTO_RAPIDO`.
- `PerfilUsuario`: `ADMINISTRADOR` (resolve alertas e gerencia usuários), `OPERADOR` (resolve alertas), `VISUALIZADOR` (só consulta).

## 5. Critérios de alerta

- Nível: um alerta ativo por tipo e dispositivo; resolvido sozinho quando o status muda.
- Conflito de sensores: não gera mais alerta, porque indutivo + capacitivo é peça metálica. `CONTAGEM_ANOMALA` fica no enum só por causa do histórico.
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
