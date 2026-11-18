# Guia de Execução — MetalTech

Passo a passo para colocar o projeto no ar, do zero, e conferir que cada parte funciona. Os detalhes de cada módulo (configuração avançada, arquitetura interna, solução de problemas específica) estão nos READMEs linkados em cada seção.

## Sumário

1. [Visão geral](#1-visão-geral)
2. [Pré-requisitos](#2-pré-requisitos)
3. [Preparação (uma vez só)](#3-preparação-uma-vez-só)
4. [Caminho A — tudo em Docker (recomendado)](#4-caminho-a--tudo-em-docker-recomendado)
5. [Caminho B — execução local por módulo (desenvolvimento)](#5-caminho-b--execução-local-por-módulo-desenvolvimento)
6. [Dados no dashboard: com ou sem a bancada](#6-dados-no-dashboard-com-ou-sem-a-bancada)
7. [Ciência de Dados](#7-ciência-de-dados)
8. [Realidade Aumentada](#8-realidade-aumentada)
9. [Testes automatizados](#9-testes-automatizados)
10. [Parar, atualizar e recomeçar](#10-parar-atualizar-e-recomeçar)
11. [Solução de problemas](#11-solução-de-problemas)

## 1. Visão geral

```text
Sensores IO-Link ──► Mestre IO-Link ──► gateway.py ──POST──► API REST :8080 ──► MySQL :3307
                                                               ▲      ▲
                                        Dashboard React :3000 ─┘      └─ Ciência de Dados (scripts Python)

App de Realidade Aumentada (GitHub Pages) ──► dados simulados (não acessa a API)
```

| Componente | Pasta | Porta | Obrigatório? |
|---|---|---|---|
| Banco MySQL 8 | [db/](../db/) | `3307` (só no próprio PC) | Sim |
| API REST (Spring Boot) | [backend/](../backend/) | `8080` | Sim |
| Dashboard (React) | [frontend/](../frontend/) | `3000` (Docker) ou `5173` (dev) | Sim |
| Gateway IO-Link | [automacao/](../automacao/) | — | Só na bancada do laboratório |
| Ciência de Dados | [dados/](../dados/) | — | Não (roda sob demanda) |
| Realidade Aumentada | [realidade-aumentada/](../realidade-aumentada/) | — | Não (já publicada) |

## 2. Pré-requisitos

| Ferramenta | Versão | Necessária para |
|---|---|---|
| Git | qualquer recente | Clonar o repositório |
| **Docker Desktop** | com Docker Compose v2 | Caminho A e o banco no Caminho B |
| JDK | **17** | Caminho B (API local) e testes |
| Node.js | **20.19+** ou **22.12+** | Caminho B (dashboard local) |
| Python | **3.11+** | Gateway e Ciência de Dados fora do Docker |

> Para só **ver o sistema funcionando**, basta o Git e o Docker Desktop (Caminho A).

Os comandos deste guia são para **PowerShell** no Windows. No Linux/macOS, troque `Copy-Item` por `cp`, `.\mvnw.cmd` por `./mvnw` e `.\.venv\Scripts\python` por `.venv/bin/python`.

## 3. Preparação (uma vez só)

```powershell
git clone https://github.com/rafarubia7/manufatura-metaltech.git
cd manufatura-metaltech
Copy-Item .env.example .env
```

Abra o `.env` e troque as senhas:

| Variável | O que é |
|---|---|
| `MYSQL_USER` | Usuário da aplicação no MySQL (padrão `metaltech`) |
| `MYSQL_PASSWORD` | Senha desse usuário |
| `MYSQL_ROOT_PASSWORD` | Senha do `root` (para o Workbench) |
| `API_KEY` | Vazia = `POST /leituras` sem autenticação. Preenchida = exige o cabeçalho `X-API-Key` |
| `COMPOSE_PROFILES` | `esteira` faz o gateway subir junto (só no servidor do laboratório) |
| `GATEWAY_MESTRE_URL` | IP do mestre IO-Link (vazio = usa o `automacao/config.json`) |

- O `.env` não vai para o Git. Sem ele, o `docker compose` para com `required variable MYSQL_PASSWORD is missing a value`.
- As senhas do MySQL **só valem na primeira subida** (volume vazio). Para trocá-las depois, veja a [seção 10](#10-parar-atualizar-e-recomeçar).

## 4. Caminho A — tudo em Docker (recomendado)

Com o Docker Desktop aberto, na raiz do repositório:

```powershell
docker compose up -d --build
docker compose ps
```

A primeira execução demora alguns minutos (download das imagens e build do Java e do React). Aguarde até `metaltech-db` e `metaltech-backend` aparecerem como **`healthy`**.

| O quê | Endereço |
|---|---|
| Dashboard | http://localhost:3000 |
| API | http://localhost:8080/api/v1 |
| Swagger (documentação interativa) | http://localhost:8080/swagger-ui.html |
| Saúde da API | http://localhost:8080/actuator/health |
| MySQL (Workbench) | `127.0.0.1:3307`, usuário `root` e a senha `MYSQL_ROOT_PASSWORD` |

**Verificação:**

```powershell
curl.exe http://localhost:8080/actuator/health              # {"status":"UP"...}
curl.exe http://localhost:8080/api/v1/leituras/estatisticas
```

No dashboard, o cabeçalho deve mostrar **"Conectado"**. Com o banco vazio, o painel mostra "Aguardando envio de leituras..." — para popular, veja a [seção 6](#6-dados-no-dashboard-com-ou-sem-a-bancada).

**Acesso por outro computador da rede:** abra `http://<ip-do-servidor>:3000` (não `localhost`) e libere as portas **3000** e **8080** no firewall do Windows do servidor.

Comandos de operação (logs, atualização, backup) estão em [arquitetura_nuvem.md, seção 2.6](arquitetura_nuvem.md#26-dockerização-comandos-de-criação-e-operação).

## 5. Caminho B — execução local por módulo (desenvolvimento)

Útil para alterar o código e ver o resultado na hora. Use **três terminais**, todos a partir da raiz do repositório.

### 5.1 Banco (terminal 1)

```powershell
docker compose up -d db
docker compose ps        # repita até "healthy"
```

### 5.2 API (terminal 2)

```powershell
cd backend
.\mvnw.cmd clean spring-boot:run
```

- Rodando a partir de `backend/`, a API lê sozinha o `../.env` (usuário, senha e `API_KEY`).
- As tabelas são criadas pelo **Flyway** na primeira subida.
- Pronta quando aparecer `Started MetaltechMonitoramentoAplicacao`. Confira com `curl.exe http://localhost:8080/actuator/health`.

Detalhes e variáveis: [backend/README.md](../backend/README.md#executar).

### 5.3 Dashboard (terminal 3)

```powershell
cd frontend
npm install
npm run dev              # http://localhost:5173
```

- `npm run dev -- --host` libera o acesso por outro computador da rede.
- Se a API estiver em outra porta, crie `frontend/.env.local` com `VITE_API_URL=http://localhost:8081` e reinicie o `npm run dev`.

Detalhes: [frontend/README.md](../frontend/README.md).

> **Não misture os caminhos:** se o Caminho A estiver no ar, a porta 8080 já está ocupada pelo contêiner `metaltech-backend`. Pare-o antes (`docker compose stop backend frontend`).

## 6. Dados no dashboard: com ou sem a bancada

### 6.1 Com a bancada (laboratório SENAI)

O gateway lê os sensores pelo mestre IO-Link (`10.110.12.41`) e envia uma leitura por peça. O PC precisa estar **na mesma rede do mestre**.

**Em Docker (servidor do laboratório):**

```powershell
Copy-Item automacao\config.example.json automacao\config.json   # ajuste mestre_url, portas e calibração
docker compose --profile esteira run --rm gateway --sondar       # confere as portas dos sensores
docker compose --profile esteira up -d --build
docker compose logs -f gateway
```

> Crie o `automacao/config.json` **antes** de subir. Se ele não existir, o Docker cria uma *pasta* com esse nome e o gateway não inicia.

**Localmente:**

```powershell
cd automacao
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
Copy-Item .\config.example.json .\config.json
.\.venv\Scripts\python gateway.py --sondar     # 1. valores brutos de cada porta
.\.venv\Scripts\python gateway.py --dry-run    # 2. fluxo completo sem enviar
.\.venv\Scripts\python gateway.py              # 3. envio real (Ctrl+C para parar)
```

Passe uma peça pela esteira: o log mostra `enviado: METALICA | nivel ... | HTTP 201` e a contagem sobe no dashboard em até 4 s.

Calibração do nível, conferência de portas e o defeito conhecido da porta X01: [automacao/README.md](../automacao/README.md#5-instalação-e-configuração).

### 6.2 Sem a bancada (demonstração)

Fora do laboratório, popule o banco com o **dataset sintético** (7 dias, ~10 mil leituras):

```powershell
docker compose run --rm dados enviar_para_api.py --url http://backend:8080
```

Ou, com Python local (ver [seção 7](#7-ciência-de-dados)):

```powershell
cd dados
.\.venv\Scripts\python scripts\enviar_para_api.py
```

Esperado: 100% de HTTP 201 e `Validação API x pandas: 23/23 checagens aprovadas`.

> **Atenção:** o script exige o **banco vazio** e grava as leituras sintéticas de verdade. Não rode no banco do laboratório. Para limpar depois: `docker compose down -v` (apaga **todos** os dados). Opções úteis: `--limite 500` (envio parcial) e `--atraso 1` (uma leitura por segundo, simulando a esteira ao vivo).

## 7. Ciência de Dados

**Em Docker (sem instalar Python):**

```powershell
docker compose run --rm dados                                                   # análise sobre o CSV
docker compose run --rm dados gerar_dataset.py                                  # regenera o dataset (semente 42)
docker compose run --rm dados analise_estatistica.py --fonte api --url http://backend:8080
```

**Localmente:**

```powershell
cd dados
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python scripts\gerar_dataset.py
.\.venv\Scripts\python scripts\analise_estatistica.py               # sobre o CSV
.\.venv\Scripts\python scripts\analise_estatistica.py --fonte api   # sobre as leituras da API
```

Saídas: gráficos em `dados/graficos/` e resultados em `dados/resultados/` (sobrescrevem os versionados). Interpretação no [Relatório de Ciência de Dados](relatorio_ciencia_dados.md).

> `--fonte api` precisa de leituras no banco e pode falhar com poucas leituras (limitação descrita em [dados/README.md](../dados/README.md#gerar-e-analisar)).

## 8. Realidade Aumentada

O app já está publicado — não precisa executar nada:

1. No celular, abra https://rafarubia7.github.io/manufatura-metaltech/realidade-aumentada/ (ou leia o QR code no fim do painel do dashboard).
2. Permita o acesso à câmera.
3. Aponte para a esteira no mesmo ângulo da foto de referência (sem a esteira, aponte para a foto na tela do computador).

Os dados exibidos são **simulados** (a rede do SENAI não é acessível pelo celular). Para testar localmente, sirva a pasta e abra no navegador do próprio PC:

```powershell
cd realidade-aumentada
python -m http.server 8000      # http://localhost:8000
```

No celular, a câmera só funciona por **HTTPS**, por isso o teste no celular é sempre pelo GitHub Pages. Checklist completo: [realidade-aumentada/README.md](../realidade-aumentada/README.md#como-testar-na-esteira).

## 9. Testes automatizados

```powershell
cd backend
.\mvnw.cmd clean verify
```

Esperado: **72 testes, 0 falhas** e `BUILD SUCCESS`. Relatório de cobertura em `backend/target/site/jacoco/index.html` (90,8% das linhas; o build falha abaixo de 70%).

- A maioria dos testes usa H2 em memória e não precisa de banco.
- `LeituraSensoresMySqlIntegracaoTest` usa Testcontainers: com o Docker Desktop fechado, ele é **ignorado** (não falha).

Lint do dashboard: `cd frontend; npm run lint`.

## 10. Parar, atualizar e recomeçar

| Objetivo | Comando |
|---|---|
| Ver o estado dos contêineres | `docker compose ps` |
| Ver logs de um serviço | `docker compose logs -f backend` |
| Aplicar mudanças no código | `docker compose up -d --build` |
| Parar, mantendo os dados | `docker compose down` |
| Parar e **apagar o banco** (zerar dados ou trocar senhas) | `docker compose down -v` |
| Parar também o gateway | `docker compose --profile esteira down` |

No Caminho B, pare a API e o dashboard com `Ctrl+C` nos terminais. Depois de atualizar o código da API, use sempre `clean` (`.\mvnw.cmd clean spring-boot:run`).

## 11. Solução de problemas

| Sintoma | Causa provável | Solução |
|---|---|---|
| `required variable MYSQL_PASSWORD is missing a value` | Falta o `.env` | `Copy-Item .env.example .env` ([seção 3](#3-preparação-uma-vez-só)) |
| `failed to connect to the docker API` | Docker Desktop fechado | Abra o Docker Desktop e aguarde ficar pronto |
| `metaltech-backend` não fica `healthy` | Banco ainda subindo ou senha errada | `docker compose logs backend`; se for `Access denied`, `docker compose down -v` e suba de novo |
| `Access denied for user 'metaltech'` (Caminho B) | API rodou fora de `backend/` ou volume criado com outra senha | Rode a partir de `backend/`; se persistir, `docker compose down -v` |
| `Port 8080 was already in use` | Contêiner `backend` ou outro processo na porta | `docker compose stop backend` ou `$env:SERVER_PORT = "8081"` + `VITE_API_URL` |
| Dashboard mostra "Sem conexão" | API fora do ar | `docker compose ps` e `curl.exe http://localhost:8080/actuator/health` |
| Funciona no servidor, mas não em outro PC | Aberto por `localhost` ou firewall | Abra pelo IP do servidor; libere as portas 3000 e 8080 |
| Painel vazio | Banco sem leituras | Gateway na bancada ou dataset sintético ([seção 6](#6-dados-no-dashboard-com-ou-sem-a-bancada)) |
| Gateway não inicia em Docker | `automacao/config.json` não existe (virou pasta) | Apague a pasta e crie o arquivo a partir do `config.example.json` |
| `API recusou (401)` no gateway ou nos scripts | `API_KEY` diferente entre cliente e API | Use o mesmo valor no `.env`, no `config.json` e em `METALTECH_API_KEY` |
| `No module named 'requests'` | Python global em vez do venv | Use `.\.venv\Scripts\python` |
| Erro de compilação do Lombok | JDK diferente do 17 | Aponte `JAVA_HOME` para o JDK 17 |

Mais casos em cada módulo: [backend](../backend/README.md#solução-de-problemas) · [frontend](../frontend/README.md#solução-de-problemas) · [automação](../automacao/README.md#8-solução-de-problemas) · [dados](../dados/README.md#solução-de-problemas).
