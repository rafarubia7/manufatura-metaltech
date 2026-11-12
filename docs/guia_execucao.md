# Guia de Execução — MetalTech

Passo a passo para subir o back-end, gerar e analisar os dados e validar a integração CIDA → API. Comandos para Windows (PowerShell); no Linux/macOS, troque `.venv\Scripts\` por `.venv/bin/` e `mvnw.cmd` por `./mvnw`.

## 1. Pré-requisitos

| Ferramenta | Versão | Uso |
|---|---|---|
| Java (JDK) | 17 | Back-end. Confira se `JAVA_HOME` aponta para o JDK 17 |
| Docker Desktop | qualquer recente | MySQL 8.0 (e opcionalmente o back-end) |
| Python | 3.11+ | Ciência de Dados |

Maven não precisa ser instalado: o projeto usa o wrapper (`mvnw`).

## 2. Ordem de execução

### Passo 1 — Testes do back-end (não precisa de banco)

```powershell
cd backend
.\mvnw.cmd test
```

Esperado: **36 testes, 0 falhas**. Os testes usam H2 em memória:

- domínio: `StatusReservatorioTest`;
- service: `LeituraSensoresServiceTest`;
- controller: `LeituraSensoresControllerTest`;
- integração sem mocks: `LeituraSensoresIntegracaoTest`.

### Passo 2 — Banco de dados

Na raiz do repositório:

```powershell
docker compose up -d mysql
docker compose ps        # aguarde o status "healthy"
```

Credenciais do MySQL:

- Banco: `metaltech`
- Usuário da aplicação: `metaltech`
- Senha da aplicação: `metaltech123`
- Porta host: `3307` (container interno: `3306`)
- Acesso local/Workbench (usuário administrativo): `root` / `root123456`

Observação: no `docker-compose.yml`, a conta do MySQL foi configurada com usuário non-root para a aplicação e com senha definida para a base local. O acesso administrativo do Workbench usa o usuário `root` com a senha `root123456`.

### Passo 3 — Back-end

Opção A, local (recomendada para desenvolvimento):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Se a porta `8080` estiver ocupada, o Spring falhará com `Web server failed to start. Port 8080 was already in use`. Nesse caso:

```powershell
cd backend
$env:SERVER_PORT = "8081"
.\mvnw.cmd spring-boot:run
```

ou pare o processo que já está usando a porta 8080 e rode novamente.

Swagger em desenvolvimento (porta padrão): http://localhost:8080/swagger-ui.html
Swagger com porta alternativa: http://localhost:8081/swagger-ui.html

Opção B, tudo em contêiner:

```powershell
docker compose up -d --build
```

Verificações:

- Swagger: http://localhost:8080/swagger-ui.html
- Listagem: http://localhost:8080/api/v1/leituras
- O Flyway cria as tabelas na primeira execução (`V1__criar_tabelas_iniciais.sql`).

Para trocar o banco sem editar arquivos, use as variáveis de ambiente `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `SERVER_PORT`.

### Passo 4 — Ambiente Python

```powershell
cd dados
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt
```

### Passo 5 — Gerar o dataset

```powershell
.venv\Scripts\python gerar_dataset.py
```

Gera `leituras_simuladas.csv` (~10 mil leituras, 7 dias) e `eventos_simulados.csv`. Com a mesma semente, o arquivo sai idêntico a cada execução. Parâmetros: `--dias`, `--seed`, `--saida`.

### Passo 6 — Análise estatística

```powershell
.venv\Scripts\python analise_estatistica.py
```

Gera os gráficos em `dados/graficos/` e os resultados em `dados/resultados/`. A interpretação está em [relatorio_ciencia_dados.md](relatorio_ciencia_dados.md).

### Passo 7 — Integração com a API (POST real)

Com o back-end rodando e o banco **vazio**:

```powershell
.venv\Scripts\python enviar_para_api.py
```

Esperado: 100% de HTTP 201 e `Validação API x pandas: 22/22 checagens aprovadas`. O script devolve código de saída 1 se alguma checagem falhar. Opções: `--limite 500` (envio parcial) e `--url`.

Para analisar os dados que já estão no banco:

```powershell
.venv\Scripts\python analise_estatistica.py --fonte api
```

### Reiniciar do zero

```powershell
docker compose down -v          # apaga o volume do MySQL
docker compose up -d mysql
```

## 3. Teste manual rápido

```powershell
curl -X POST http://localhost:8080/api/v1/leituras -H "Content-Type: application/json" -d '{"timestamp":"2026-11-03T10:00:00Z","sensorDistancia":{"valor":9.5,"unidade":"cm","status":"OK"},"sensorIndutivo":{"detectouMetal":true,"intensidade":0.85,"status":"OK"},"sensorCapacitivo":{"detectouNaoMetal":false,"intensidade":0.1,"status":"OK"},"peca":{"id":"peca-1","tipo":"METALICA","timestampDetectado":"2026-11-03T10:00:00Z"},"nivelReservatorio":{"nivelAtual":97,"capacidadeMaxima":100,"percentual":97,"status":"NORMAL"},"dispositivoId":"ESP32-001","versaoFirmware":"1.0.0"}'
curl http://localhost:8080/api/v1/leituras/alertas
curl http://localhost:8080/api/v1/leituras/estatisticas
```

Resultado esperado:

1. HTTP 201, com status recalculado para `CRITICO` (o dispositivo informou `NORMAL`) e um alerta `RESERVATORIO_CRITICO`.
2. Repetir o POST não cria um segundo alerta.

## 4. Solução de problemas

| Sintoma | Causa provável | Solução |
|---|---|---|
| `Connection to localhost:3306 refused` | MySQL não está rodando | `docker compose up -d mysql` |
| `failed to connect to the docker API` | Docker Desktop fechado | Abra o Docker Desktop e aguarde ficar pronto |
| `Unsupported Database: MySQL` | Falta o módulo do Flyway | Já corrigido no `pom.xml` (`flyway-mysql`) |
| `Communications link failure` | MySQL em pé, mas sem acesso/credenciais | Verifique `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` |
| `Access denied for user 'root'@'localhost'` | Usuário ou senha do Workbench errados | Use `root / root123456` para conexão local do Workbench |
| Erro de compilação do Lombok | JDK ≠ 17 | Aponte `JAVA_HOME` para o JDK 17 |
| Validação acusa total diferente | Banco tinha leituras anteriores | Reinicie do zero (seção acima) |
| Acentos quebrados no console | Codificação do terminal | Os scripts já forçam UTF-8; use o Windows Terminal |

## 5. Status final do projeto (etapa Back-end + CIDA)

### Concluído

**Back-end**

- API REST validada contra MySQL real:
  - status do reservatório calculado no domínio;
  - alertas persistidos em cascata, deduplicados e resolvidos automaticamente;
  - estatísticas ampliadas;
  - Swagger documentado.
- Testes automatizados: 36 testes, incluindo a integração ponta a ponta sem mocks.
- `docker-compose.yml` e configuração por variáveis de ambiente.

**Ciência de Dados**

- Dataset reproduzível com padrões operacionais e anomalias rotuladas.
- Análise estatística, detecção e avaliação de outliers, 10 gráficos e relatório técnico.
- Integração CIDA → API validada (22/22).

### Pendente / próximas etapas

| Item | Observação |
|---|---|
| Front-end / dashboard | Consumir `/estatisticas` e `/alertas` (contrato estável) |
| Realidade Aumentada | Depende do front-end e da API |
| Nuvem | Dockerfile e compose prontos; falta pipeline de deploy |
| Autenticação (JWT) | Prevista no plano técnico para a fase 2 |
| Observabilidade | Adicionar `spring-boot-starter-actuator` (health, métricas); hoje `/actuator` não está exposto |
| Revisão da política de alertas | Recomendações na seção 7 do relatório (alerta baixo informativo, agrupar conflitos, alerta preditivo de transbordo) |
| Dados reais | Recalibrar os parâmetros da simulação com leituras do ESP32 |
