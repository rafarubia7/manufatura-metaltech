# Back-end — API REST MetalTech

API REST em Java 17 / Spring Boot 3.2 que centraliza os dados da esteira: recebe as leituras do gateway, classifica as peças, calcula o status do reservatório, gera alertas e fornece os dados ao dashboard e à Ciência de Dados. Banco MySQL 8.0 com migrações Flyway.

Documentação interativa (Swagger): `http://localhost:8080/swagger-ui.html`.

## Organização do código

```text
src/main/java/com/metaltech/monitoramento/
├── domain/
│   ├── model/        LeituraSensores, AlertaNivel, Usuario e enums (TipoPeca, StatusReservatorio, TipoAlerta, PerfilUsuario)
│   └── sensor/       Sensor (abstrata) → SensorIndutivo / SensorCapacitivo, ClassificadorPeca
├── application/service/   LeituraSensoresService, EstatisticasService, EsteiraStatusService, UsuarioService
├── api/
│   ├── controller/   LeituraSensoresController, EsteiraController, UsuarioController
│   ├── dto/ mapper/  entrada e saída em JSON
│   ├── config/       ApiKeyFilter (X-API-Key) e WebConfig (CORS)
│   └── exception/    TratadorGlobalExcecoes (formato único de erro)
└── infrastructure/repository/   repositórios Spring Data JPA
src/main/resources/db/migration/  V1 (tabelas), V2 (acentuação e reclassificação de AMBAS), V3 (usuários)
```

**Orientação a objetos:**
- **Herança e polimorfismo:** `Sensor` é abstrata. Cada subclasse (`SensorIndutivo`, `SensorCapacitivo`) diz qual tipo de peça o seu acionamento indica, e o `ClassificadorPeca` combina os sensores sem conhecer as subclasses.
- **Encapsulamento:** `Usuario` não tem setters. Ele só muda por métodos próprios (`atualizar`, `ativar`, `desativar`), exige perfil, normaliza o e-mail e decide as permissões a partir do perfil (um usuário inativo perde todas). `LeituraSensores` controla a lista de alertas por `adicionarAlerta` e `removerAlerta`.

## Executar

### 1. Credenciais (`.env`)

O `compose.yaml` **não sobe** sem o `.env` na raiz do repositório. O `.env.example` é só o modelo.

```powershell
# na raiz do repositório
Copy-Item .env.example .env
```

Os valores de exemplo servem para desenvolvimento local. Para qualquer outro uso, troque as senhas. O `.env` não é versionado.

### 2. Banco de dados

```powershell
docker compose up -d db
docker compose ps        # repita até o status ficar "healthy"
```

- Banco `metaltech`, porta `3307` no host (`3306` dentro do contêiner).
- Usuário da aplicação: `MYSQL_USER` / `MYSQL_PASSWORD` do `.env`. Administração (Workbench): `root` / `MYSQL_ROOT_PASSWORD`.
- As senhas só valem na criação do volume. Para trocá-las, ou para zerar o banco: `docker compose down -v` (apaga os dados) e suba de novo.
- Na primeira subida, o MySQL roda o `db/init.sql` (charset `utf8mb4`). As tabelas são criadas pelo Flyway quando a API sobe.

### 3. Testes

```powershell
cd backend
.\mvnw.cmd clean verify
```

Esperado: **72 testes, 0 falhas**, `BUILD SUCCESS` e o relatório de cobertura JaCoCo em `target/site/jacoco/index.html`. A cobertura atual é de 90,8% das linhas, e o build falha abaixo de 70%.

- A maioria dos testes usa H2 em memória (domínio, serviços, controllers, filtro de API key, endpoint da esteira e integração sem mocks).
- `LeituraSensoresMySqlIntegracaoTest` sobe um MySQL temporário com Testcontainers. Precisa do **Docker Desktop aberto**. Sem Docker, esses testes são ignorados (não falham).

### 4. Subir a API

**Local (desenvolvimento).** Com o banco no ar:

```powershell
cd backend
.\mvnw.cmd clean spring-boot:run
```

Rodando a partir de `backend/`, a API lê sozinha o `../.env` (`spring.config.import` no `application.yml`): usa `MYSQL_USER`, `MYSQL_PASSWORD` e `API_KEY` de lá. Variáveis de ambiente da sessão têm prioridade sobre o arquivo. Use sempre `clean` depois de atualizar o código, para recompilar todas as classes. Se a porta 8080 estiver ocupada, use `$env:SERVER_PORT = "8081"` e ajuste os clientes (`VITE_API_URL` no front e `api_url` no gateway).

**Em contêiner.** Na raiz, `docker compose up -d --build` sobe banco, API e dashboard. O compose já lê o `.env`.

| Variável de ambiente | Uso |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexão com o MySQL. Sem elas, a API usa `localhost:3307` e o `MYSQL_USER`/`MYSQL_PASSWORD` do `.env` |
| `SERVER_PORT` | Porta da API (padrão `8080`) |
| `API_KEY` | Vazia = `POST /leituras` aberto (só desenvolvimento). Preenchida = exige o cabeçalho `X-API-Key` |
| `CORS_ORIGENS` | Origens liberadas, separadas por vírgula (padrão `*`; em produção, só o domínio do dashboard) |

### 5. Verificação rápida

```powershell
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/api/v1/leituras/estatisticas
curl.exe http://localhost:8080/api/v1/leituras/alertas
curl.exe http://localhost:8080/api/v1/esteira/status    # 404 enquanto não houver leituras
```

No PowerShell, `curl` é um apelido de `Invoke-WebRequest`, por isso o comando é `curl.exe`. Para testar o `POST /api/v1/leituras` manualmente, use o Swagger.

## Contrato da API (v1)

Base: `http://<host>:8080/api/v1`. JSON em UTF-8. Datas em ISO-8601 UTC com `Z`. Contrato adotado pela Automação (gateway), pelo back-end, pelo dashboard e pela Ciência de Dados.

### 1. Semântica de uma leitura

- **Uma leitura = uma peça** que passou pelo ponto de sensoriamento, mais o nível do reservatório naquele instante.
- O dispositivo envia o `POST` **quando detecta uma peça**. Amostras periódicas sem peça **não** devem ser enviadas: elas seriam contadas como `DESCONHECIDA` e distorceriam a contagem.
- O tipo da peça é **derivado dos sensores pelo back-end**:

| Indutivo acionado | Capacitivo acionado | `tipo` |
|---|---|---|
| sim | não | `METALICA` |
| não | sim | `NAO_METALICA` |
| sim | sim | `METALICA` (o capacitivo também reage a metal; o indutivo decide) |
| não | não | `DESCONHECIDA` |

- `AMBAS` continua no enum só por compatibilidade com dados antigos: o back-end não gera mais esse tipo (a migração `V2` reclassificou as leituras antigas).
- O `tipo` e o `status` enviados pelo dispositivo são **informativos**: o back-end recalcula e registra divergência no log.
- `percentual` (0 a 100) é calculado pelo dispositivo; o back-end confia nele.

### 2. Enviar leitura

`POST /leituras`. Corpo:

```json
{
  "timestamp": "2026-11-03T08:00:00Z",
  "sensorDistancia": { "valor": 9.5, "unidade": "cm", "status": "OK" },
  "sensorIndutivo": { "detectouMetal": true, "intensidade": 0.82, "status": "OK" },
  "sensorCapacitivo": { "detectouNaoMetal": false, "intensidade": 0.06, "status": "OK" },
  "peca": { "id": "peca-1", "tipo": "METALICA", "timestampDetectado": "2026-11-03T08:00:00Z" },
  "nivelReservatorio": { "nivelAtual": 97.0, "capacidadeMaxima": 100.0, "percentual": 97.0, "status": "CRITICO" },
  "dispositivoId": "gateway-lab-01",
  "versaoFirmware": "gateway-0.2"
}
```

Quando o back-end tiver `API_KEY` configurada, este `POST` exige o cabeçalho `X-API-Key: <chave>`. Sem ele, ou com chave errada, a resposta é `401` (`NAO_AUTORIZADO`). As consultas (`GET`) não exigem chave.

Resposta `201` com a leitura persistida (campo `id`) e a lista `alertas` gerados.

### 3. Endpoints

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
| GET | `/esteira/status` | **Estado atual** em uma chamada (criado para o R.A; ver seção 7) |
| GET | `/usuarios` | Listar usuários (ordem alfabética) |
| GET | `/usuarios/{id}` | Buscar usuário |
| POST | `/usuarios` | Cadastrar usuário |
| PUT | `/usuarios/{id}` | Atualizar usuário (e ativar/desativar) |
| DELETE | `/usuarios/{id}` | Excluir usuário |

Fora de `/api/v1`, o `GET /actuator/health` responde com `"status":"UP"` quando a API está no ar (usado pelo healthcheck do Docker).

**Ordenação de `GET /leituras`:** **sem filtros**, a lista vem sempre da leitura mais recente para a mais antiga (o dashboard e a Ciência de Dados dependem disso), e o parâmetro `sort` é ignorado. **Com filtros** (`inicio`/`fim`, `tipoPeca` ou `statusReservatorio`), vale o `sort` informado, e o padrão é `dataHora` crescente.

**Corpo de `POST`/`PUT /usuarios`:**

```json
{ "nome": "Ana Souza", "email": "ana@metaltech.com", "perfil": "OPERADOR", "ativo": true }
```

- O `email` é único e gravado em minúsculas. Repetido → 400 `REQUISICAO_INVALIDA`.
- O `ativo` é opcional e só vale no `PUT` (nulo = não muda).
- A resposta inclui `id`, `criadoEm` e as permissões derivadas do perfil (`podeResolverAlertas`, `podeGerenciarUsuarios`). Usuário inativo perde as permissões.

### 4. Estado atual da esteira

`GET /esteira/status` devolve, em uma chamada, o que uma tela de campo precisa (seção 5.5 do [enunciado](../docs/projeto.md)):

```json
{
  "ultimaLeitura": "2026-11-08T23:58:41Z",
  "dispositivoId": "gateway-lab-01",
  "nivelReservatorioPercentual": 82.5,
  "statusReservatorio": "ALERTA_ALTO",
  "totalMetalicas": 6000,
  "totalNaoMetalicas": 3303,
  "totalDesconhecidas": 613,
  "totalAmbas": 0,
  "alertasAtivos": 2,
  "tiposAlertaAtivos": ["ENCHIMENTO_RAPIDO", "RESERVATORIO_ALTO"],
  "exibirAlerta": true
}
```

- `exibirAlerta` é `true` quando o reservatório está **perto da capacidade máxima** (`ALERTA_ALTO` ou `CRITICO`) **ou fora do padrão identificado pela Ciência de Dados** (alerta `ENCHIMENTO_RAPIDO` ativo, enchimento acima de 5 %/min).
- `tiposAlertaAtivos` diz o motivo, para mostrar a mensagem certa.
- `totalAmbas` fica sempre em 0 nos dados novos (indutivo + capacitivo = metálica).
- Responde `404` (`NAO_ENCONTRADO`) enquanto não existir nenhuma leitura. Para acompanhar, use polling a cada poucos segundos.

### 5. Enums e critérios de alerta

- `TipoPeca`: `METALICA`, `NAO_METALICA`, `DESCONHECIDA`, `AMBAS` (só histórico).
- `StatusReservatorio`: `NORMAL`, `ALERTA_BAIXO` (< 20%), `ALERTA_ALTO` (> 80%), `CRITICO` (≥ 95%).
- `TipoAlerta`: `RESERVATORIO_BAIXO`, `RESERVATORIO_ALTO`, `RESERVATORIO_CRITICO`, `CONTAGEM_ANOMALA` (só histórico), `ENCHIMENTO_RAPIDO`.
- `PerfilUsuario`: `ADMINISTRADOR` (resolve alertas e gerencia usuários), `OPERADOR` (resolve alertas), `VISUALIZADOR` (só consulta).

Critérios:
- **Nível:** um alerta ativo por tipo e dispositivo, resolvido sozinho quando o status muda.
- **Conflito de sensores:** não gera mais alerta, porque indutivo + capacitivo é peça metálica.
- **Enchimento rápido:** taxa acima de **5 %/min**, medida entre a leitura atual e a primeira leitura dos 3 minutos anteriores (mínimo de 1 minuto entre elas). Um alerta por episódio. Critério definido pela Ciência de Dados ([relatório](../docs/relatorio_ciencia_dados.md), seção 6.3).

### 6. Erros

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

### 7. Consumidores da API

| Cliente | Endpoints | Situação |
|---|---|---|
| Gateway IO-Link ([automacao/](../automacao/)) | `POST /leituras` com `X-API-Key` | Em uso, com dados reais da bancada |
| Dashboard React ([frontend/](../frontend/)) | `GET /leituras`, `GET /leituras/estatisticas`, `GET /leituras/alertas`, `PUT /leituras/alertas/{id}/resolver`, CRUD de `/usuarios` | Em uso |
| Ciência de Dados ([dados/](../dados/)) | `POST /leituras`, `GET /leituras/estatisticas` e `GET /leituras/alertas` (`enviar_para_api.py`, envio e validação); `GET /leituras` (`analise_estatistica.py --fonte api`) | Em uso |
| Docker Compose | `GET /actuator/health` | Em uso (healthcheck) |
| Realidade Aumentada ([realidade-aumentada/](../realidade-aumentada/)) | — | **Não consome.** A bancada e a API ficam na rede do SENAI, e o celular não tem acesso a ela. O R.A simula o formato de `/esteira/status` |

O `/esteira/status` foi criado para o R.A e é coberto por testes (`EsteiraControllerTest`), mas hoje nenhum cliente o chama: o dashboard monta o painel com `/leituras/estatisticas` e `/leituras`.

**Mapeamento da simulação do R.A para a API.** A função `obterDados()` de `realidade-aumentada/js/dados.js` já devolve os dados de `/esteira/status`, com outros nomes. Para integrar no futuro, basta converter:

| Campo do R.A (`obterDados()`) | Campo de `/esteira/status` |
|---|---|
| `metalicas` | `totalMetalicas` |
| `naoMetalicas` | `totalNaoMetalicas` |
| `nivelReservatorio` | `nivelReservatorioPercentual` |
| `status` | `statusReservatorio` |
| `exibirAlerta` | `exibirAlerta` |
| `motivos` | `tiposAlertaAtivos` |
| `ultimaLeitura` | `ultimaLeitura` |

### 8. Mudanças no contrato

Qualquer mudança que quebre este contrato é combinada com toda a equipe antes de entrar na `main`.

## Solução de problemas

| Sintoma | Causa provável | Solução |
|---|---|---|
| `required variable MYSQL_PASSWORD is missing a value` | Não existe o arquivo `.env` | `Copy-Item .env.example .env` (passo 1) |
| `failed to connect to the docker API` | Docker Desktop fechado | Abra o Docker Desktop e aguarde ficar pronto |
| `Connection to localhost:3306 refused` | MySQL parado ou porta errada | `docker compose up -d db`; no host, a porta é `3307` |
| `Access denied for user 'metaltech'` | A API rodou fora de `backend/` (não achou o `../.env`), ou o volume do MySQL foi criado com outra senha | Rode a partir de `backend/`; se persistir, `docker compose down -v` e suba o banco de novo |
| `Access denied for user 'root'@'localhost'` | Usuário ou senha do Workbench errados | Use `root` e a senha de `MYSQL_ROOT_PASSWORD` |
| `Communications link failure` | MySQL no ar, mas sem acesso ou credenciais | Confira `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` |
| `Port 8080 was already in use` | Outro processo na porta | `$env:SERVER_PORT = "8081"` e ajuste os clientes |
| Erro de compilação do Lombok | JDK diferente do 17 | Aponte `JAVA_HOME` para o JDK 17 (o `mvnw` usa essa variável) |
| Alertas com `ReservatÃ³rio` / `peÃ§a` | Classes antigas compiladas sem UTF-8 | `.\mvnw.cmd clean spring-boot:run`; a migração V2 corrige os textos já gravados |
