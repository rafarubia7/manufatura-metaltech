# Guia de Execução — MetalTech

Passo a passo para subir o banco e o back-end, rodar o front-end, ligar o gateway IO-Link (dados reais dos sensores) e executar a Ciência de Dados (dataset sintético, análise e relatório). Todos os comandos são para **Windows PowerShell**; no Linux/macOS, troque `.\.venv\Scripts\python` por `./.venv/bin/python` e `mvnw.cmd` por `./mvnw`.

> **Atenção ao `.\` no PowerShell.** Para executar um programa por caminho relativo, o PowerShell exige o prefixo `.\`. Sem ele, `.venv\Scripts\pip` é lido como "módulo `.venv`, comando `Scripts\pip`" e dá o erro `Não foi possível carregar o módulo '.venv'`. Escreva sempre `.\.venv\Scripts\...`.

## 1. Pré-requisitos

| Ferramenta | Versão | Uso |
|---|---|---|
| Java (JDK) | 17 | Back-end. `JAVA_HOME` precisa apontar para o JDK 17 (o `mvnw` usa essa variável, mesmo que `java -version` mostre outra versão) |
| Docker Desktop | qualquer recente | MySQL 8.0, back-end em contêiner e testes de integração com MySQL |
| Python | 3.11+ | Gateway IO-Link e Ciência de Dados. Confira com `python --version` |
| Node.js | 20+ | Front-end (`npm`) |

Maven não precisa ser instalado: o projeto usa o wrapper (`mvnw`).

Se `python --version` abrir a Microsoft Store, o Python não está instalado: instale-o em python.org (marque **Add python.exe to PATH**) ou desative o atalho em *Configurações → Aplicativos → Configurações avançadas → Aliases de execução de aplicativo*.

## 2. Mapa do repositório e endereços

| Pasta | O que é | Seção |
|---|---|---|
| `backend/` | API REST (Spring Boot) | 3 |
| `frontend/` | Dashboard web (React + Vite) | 4 |
| `automacao/` | Gateway que lê o mestre IO-Link e envia leituras reais à API | 5 |
| `dados/` | Ciência de Dados: dataset sintético, análise estatística, envio à API e ponte IO-Link alternativa | 6 |

Endereços:

| O quê | Endereço |
|---|---|
| API | `http://localhost:8080` no PC do back-end. De outro PC, `http://<IP-do-PC-do-backend>:8080` (descubra com `ipconfig`) |
| Mestre IO-Link | `http://10.110.12.41` (aparelho de rede, IP fixo; se mudar, edite `mestre_url` em `automacao/config.json`) |
| MySQL (host) | porta `3307` |

Não é preciso editar IP da API em lugar nenhum enquanto o gateway, o back-end e o front-end rodarem no mesmo PC: o gateway usa `localhost` e o front-end usa o mesmo host em que a página foi aberta. Só o acesso de **outro** PC exige o IP do PC do back-end.

## 3. Back-end

### Passo 0 — Credenciais (`.env`)

O `compose.yaml` **não sobe** sem o arquivo `.env` na raiz do repositório. O `.env.example` é só o modelo: o Docker não o lê.

```powershell
Copy-Item .env.example .env
```

Os valores de exemplo servem para desenvolvimento local. Para qualquer outro uso, troque as senhas no `.env`. O arquivo não é versionado.

### Passo 1 — Testes do back-end

```powershell
cd backend
.\mvnw.cmd test
```

Esperado: **60 testes, 0 falhas**, terminando em `BUILD SUCCESS`.

- A maioria usa H2 em memória e não precisa de banco: domínio, serviços, controllers, filtro de API key, endpoint da esteira e integração sem mocks.
- `LeituraSensoresMySqlIntegracaoTest` (2 testes) sobe um MySQL temporário via Testcontainers. Precisa do **Docker Desktop aberto**; sem Docker, esses 2 testes são ignorados (não falham).

### Passo 2 — Banco de dados

Na raiz do repositório:

```powershell
docker compose up -d db
docker compose ps        # repita até o status ficar "healthy"
```

Dados do MySQL (definidos no `.env`):

- Banco: `metaltech`
- Usuário da aplicação: valor de `MYSQL_USER`
- Senha da aplicação: valor de `MYSQL_PASSWORD`
- Porta no host: `3307` (dentro do contêiner: `3306`)
- Acesso administrativo (Workbench): usuário `root` e a senha de `MYSQL_ROOT_PASSWORD`

As senhas só valem na criação do volume. Para trocá-las depois, rode `docker compose down -v` (apaga os dados) e suba de novo.

### Passo 3 — Back-end

**Opção A, local (recomendada para desenvolvimento).** O Spring **não lê o arquivo `.env`**; ele lê variáveis de ambiente. Sem a senha na sessão, a conexão falha com `Access denied`. Carregue o `.env` na sessão do PowerShell antes de subir (uma vez por terminal):

```powershell
# na raiz do repositório
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$') { Set-Item "env:$($Matches[1])" $Matches[2].Trim() }
}
cd backend
.\mvnw.cmd spring-boot:run
```

Se a porta `8080` estiver ocupada (`Web server failed to start. Port 8080 was already in use`), use outra e ajuste os clientes (seção 2):

```powershell
$env:SERVER_PORT = "8081"
.\mvnw.cmd spring-boot:run
```

**Opção B, tudo em contêiner.** Esta forma lê o `.env` sozinha e sobe o banco (`db`), a API (`backend`) e o dashboard (`frontend`) na rede interna `rede_interna`:

```powershell
docker compose up -d --build
docker compose ps -a     # db "healthy", backend e frontend "Up"
docker compose logs -f backend
```

- Dashboard: http://localhost:3000 (Nginx; chama a API em `:8080` no mesmo host)
- Na primeira subida, com o volume `mysql_data` vazio, o MySQL roda o `db/init.sql` (charset `utf8mb4`). As tabelas são criadas pelo Flyway quando o back-end sobe.

Verificações:

- Swagger: http://localhost:8080/swagger-ui.html
- Listagem: http://localhost:8080/api/v1/leituras
- Estado da esteira: http://localhost:8080/api/v1/esteira/status (retorna 404 enquanto não houver nenhuma leitura)
- O Flyway cria as tabelas na primeira execução (`V1__criar_tabelas_iniciais.sql`).

Variáveis de ambiente aceitas pelo back-end: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`, `API_KEY` e `CORS_ORIGENS`.

**API key (opcional).** Com `API_KEY` vazia, o `POST /api/v1/leituras` é aberto (só para desenvolvimento). Com `API_KEY` preenchida, o POST exige o cabeçalho `X-API-Key`. Veja o aviso sobre o gateway na seção 5.

### Reiniciar o banco do zero

```powershell
docker compose down -v          # apaga o volume do MySQL
docker compose up -d db
```

## 4. Front-end (dashboard)

Com o back-end rodando:

```powershell
cd frontend
npm install
npm run dev
```

O Vite informa o endereço (normalmente http://localhost:5173).

Sem configuração, o front-end chama a API no mesmo host em que a página foi aberta, na porta 8080 (`localhost` no próprio PC). Para abri-lo a partir de outro PC, suba o Vite expondo a rede (`npm run dev -- --host`) e acesse pelo IP do PC do back-end; o front-end reaproveita esse IP.

Para apontar para uma API em outro endereço ou porta, crie `frontend/.env.local` (ignorado pelo Git):

```
VITE_API_URL=http://localhost:8081
```

Depois de alterar variáveis, reinicie o `npm run dev`. Detalhes da integração em [guia_frontend.md](guia_frontend.md).

## 5. Gateway IO-Link (laboratório)

Lê os 3 sensores do mestre IO-Link e envia um POST por peça à API. Só funciona no PC ligado à rede do mestre (`mestre_url` no `config.json`) e com o back-end no ar.

### Instalação

```powershell
cd automacao
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
Copy-Item .\config.example.json .\config.json    # só se o config.json não existir
```

Rode sempre com `.\.venv\Scripts\python`. Se usar `py` ou `python` direto, o `requests` não será encontrado, pois está instalado só no `.venv`.

### Configuração

Edite o `config.json`:

- `api_url`: `http://localhost:8080/api/v1/leituras` (o gateway roda no mesmo PC do back-end; não precisa de IP)
- `portas`: em qual porta do mestre cada sensor está ligado. No laboratório: distância na `1`, capacitivo na `2` e indutivo na `3`.
- `distancia.vazio_raw` e `distancia.cheio_raw`: calibração do nível (veja abaixo).

**Conferir as portas.** Com a esteira vazia, rode `--sondar`. O indutivo livre lê um valor alto (cerca de `32736`) e o capacitivo livre lê um valor baixo (abaixo de `1000`). Se aparecer o contrário, as portas estão trocadas no `config.json`. Com as portas trocadas, o gateway enxerga "metal e não metal ao mesmo tempo" mesmo sem peça, e envia uma leitura falsa ao iniciar.

**Calibrar o nível.** O nível do reservatório é calculado entre `vazio_raw` e `cheio_raw`. Com o reservatório vazio, leia a porta de distância no `--sondar` e anote o valor (`vazio_raw`); com ele cheio, anote de novo (`cheio_raw`). Se a leitura real estiver fora dessa faixa, o nível fica travado em 0% (ou 100%) e a API gera alertas de reservatório baixo/alto sem motivo.

### Execução

```powershell
.\.venv\Scripts\python gateway.py --sondar      # mostra o valor bruto de cada porta (comece por aqui)
.\.venv\Scripts\python gateway.py --sondar --continuo   # repete a cada segundo: aproxime uma peça e veja os valores mudarem
.\.venv\Scripts\python gateway.py --dry-run     # roda o fluxo completo e imprime o JSON em vez de enviar
.\.venv\Scripts\python gateway.py               # envio real, em loop (Ctrl+C para parar)
```

Com `heartbeat_s: 0`, o gateway envia **somente quando uma peça passa** pelos sensores. Ao iniciar, ele mostra `gateway iniciado` e `aguardando pecas`; depois fica quieto até uma peça passar, quando aparecem `peca detectada`, `classificada: METALICA (na janela: indutivo=True capacitivo=True)` e `enviado: METALICA | nivel ... | HTTP 201`.

### Como confirmar que funcionou

1. Deixe o gateway rodando e passe uma peça pelos sensores.
2. Confira o total na API:

```powershell
curl.exe http://localhost:8080/api/v1/leituras/estatisticas
curl.exe http://localhost:8080/api/v1/esteira/status
```

3. Atualize o dashboard: a peça deve aparecer na contagem.

Se aparecer `falha ao enviar` no gateway, a API está inacessível (back-end parado, IP/porta errados ou firewall do Windows bloqueando a porta 8080). `API recusou (4xx)` indica payload ou chave recusada.

> **Chave do dispositivo.** Se o back-end tiver `API_KEY` preenchida no `.env`, coloque o mesmo valor em `api_key` no `automacao/config.json`. O gateway envia a chave no cabeçalho `X-API-Key`, que é o que o back-end valida. Chave diferente ou ausente → `API recusou (401)`.

Mais detalhes em [automacao/README.md](../automacao/README.md).

## 6. Ciência de Dados (`dados/`)

O dataset sintético é a base reproduzível da análise e do relatório ([relatorio_ciencia_dados.md](relatorio_ciencia_dados.md)). As leituras reais do gateway servem para comparar.

### Ambiente

```powershell
cd dados
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
```

### Gerar o dataset sintético

```powershell
.\.venv\Scripts\python gerar_dataset.py
```

Gera `leituras_simuladas.csv` (~10 mil leituras, 7 dias) e `eventos_simulados.csv`. Com a mesma semente, o arquivo sai idêntico a cada execução. Parâmetros: `--dias`, `--seed`, `--saida`.

### Análise estatística

```powershell
.\.venv\Scripts\python analise_estatistica.py                # sobre o dataset sintético (CSV)
.\.venv\Scripts\python analise_estatistica.py --fonte api    # sobre as leituras reais gravadas na API
```

Gera os gráficos em `dados/graficos/` e os resultados em `dados/resultados/`. A interpretação está em [relatorio_ciencia_dados.md](relatorio_ciencia_dados.md). Com `--fonte api` e o banco vazio, o script não encontra leituras: rode antes o gateway (seção 5).

### Validar a API com o dataset sintético (opcional)

```powershell
.\.venv\Scripts\python enviar_para_api.py
```

Esperado: 100% de HTTP 201 e `Validação API x pandas: 22/22 checagens aprovadas`.

> **Cuidado:** este script **grava as ~10 mil leituras sintéticas no banco** e exige o banco vazio. Elas aparecem no dashboard misturadas às leituras reais do gateway. Use apenas para validar a API e depois limpe com `docker compose down -v` (apaga os dados), ou rode contra um banco de teste. Opções: `--limite 500` (envio parcial) e `--url` (outro endereço). Se o back-end tiver `API_KEY`, defina antes `$env:METALTECH_API_KEY = "<a mesma chave>"`.

### Ponte IO-Link alternativa e seus testes

`script_ponte_iolink.py` é uma ponte IO-Link (HEX) → API. O caminho em uso no laboratório é o `automacao/gateway.py`. Testes da ponte:

```powershell
.\.venv\Scripts\python -m pip install -r requirements-dev.txt
.\.venv\Scripts\python -m pytest
```

## 7. Verificação rápida da API

```powershell
curl.exe http://localhost:8080/api/v1/leituras/alertas
curl.exe http://localhost:8080/api/v1/leituras/estatisticas
curl.exe http://localhost:8080/api/v1/esteira/status
```

No PowerShell, `curl` é um apelido de `Invoke-WebRequest`; por isso o comando é `curl.exe`. Para testar o `POST /api/v1/leituras` manualmente, use o Swagger.

## 8. Solução de problemas

| Sintoma | Causa provável | Solução |
|---|---|---|
| `required variable MYSQL_PASSWORD is missing a value` | Não existe o arquivo `.env` | `Copy-Item .env.example .env` (Passo 0) |
| `Não foi possível carregar o módulo '.venv'` | Faltou o `.\` no caminho | Use `.\.venv\Scripts\python -m pip ...` |
| `No module named 'requests'` ao rodar o gateway | Rodou com `py`/`python` global, não com o venv | Use `.\.venv\Scripts\python gateway.py` |
| `Python was not found` / abre a Microsoft Store | Python não instalado ou só o atalho da Store | Instale o Python (seção 1) |
| `a execução de scripts foi desabilitada` ao ativar o venv | Política de execução do PowerShell | Não ative o venv: chame `.\.venv\Scripts\python` direto |
| `Connection to localhost:3306 refused` | MySQL não está rodando, ou porta errada | `docker compose up -d db`; o host usa a porta `3307` |
| `failed to connect to the docker API` | Docker Desktop fechado | Abra o Docker Desktop e aguarde ficar pronto |
| `Access denied for user 'metaltech'` ao subir o back-end local | A senha do `.env` não foi para a sessão, ou o volume foi criado com outra senha | Rode o carregamento do `.env` (Passo 3); se persistir, `docker compose down -v` |
| `Access denied for user 'root'@'localhost'` | Usuário ou senha do Workbench errados | Use `root` e a senha de `MYSQL_ROOT_PASSWORD` (arquivo `.env`) |
| `Communications link failure` | MySQL em pé, mas sem acesso/credenciais | Verifique `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` |
| `Unsupported Database: MySQL` | Falta o módulo do Flyway | Já corrigido no `pom.xml` (`flyway-mysql`) |
| Erro de compilação do Lombok | JDK ≠ 17 | Aponte `JAVA_HOME` para o JDK 17 |
| Front-end mostra erro de rede ou lista vazia | `VITE_API_URL` aponta para outra porta ou máquina | Confira `frontend/.env`; reinicie o `npm run dev` |
| Gateway parado depois de `aguardando pecas` | Normal: só envia quando uma peça passa | Passe uma peça (ou veja os sensores com `--sondar --continuo`) |
| Gateway envia uma leitura `METALICA` com a esteira vazia | Portas do indutivo e capacitivo trocadas no `config.json` | Confira com `--sondar` (seção 5) |
| Nível sempre 0% e alerta de reservatório baixo | `vazio_raw`/`cheio_raw` fora da faixa real | Recalibre (seção 5) |
| Gateway: `falha ao enviar` | API inacessível pelo IP | Back-end no ar? Firewall liberando a porta 8080? |
| Gateway recebe HTTP 401 | `API_KEY` do back-end diferente do `api_key` do `config.json` | Use o mesmo valor nos dois (seção 5) |
| `analise_estatistica.py --fonte api`: erro ou sem dados | O banco ainda está vazio | Rode o gateway e passe peças |
| Dashboard mostra milhares de leituras que não são dos sensores | Rodou `enviar_para_api.py` (dados sintéticos) no banco real | `docker compose down -v` e suba o MySQL de novo |
| Acentos quebrados no console | Codificação do terminal | Os scripts já forçam UTF-8; use o Windows Terminal |
| Alertas com `ReservatÃ³rio` / `peÃ§a` no dashboard | Classes antigas compiladas sem UTF-8 (antes do ajuste no `pom.xml`) | Pare a API e rode `.\mvnw.cmd clean spring-boot:run`; a migration V2 corrige os textos já gravados |

## 9. Status do projeto

### Concluído

- **Back-end:** API REST validada contra MySQL real, com status do reservatório calculado no domínio, alertas deduplicados e resolvidos automaticamente, estatísticas, endpoint `/esteira/status` para dashboard e RA, CRUD de usuários com perfis, API key opcional no POST, health check (`/actuator/health`) e Swagger. Testes automatizados incluindo integração contra MySQL real (Testcontainers).
- **Front-end:** dashboard em React/Vite com React Router (4 rotas), Bootstrap e atualização automática.
- **Automação:** gateway IO-Link lendo os sensores reais e enviando à API.
- **Ciência de Dados:** dataset sintético reproduzível, análise estatística, gráficos, relatório e validação API x pandas; análise também disponível sobre as leituras reais (`--fonte api`).

### Pendente / próximas etapas

| Item | Observação |
|---|---|
| Calibração do nível | Definir `vazio_raw` e `cheio_raw` com o reservatório real (seção 5) |
| Nuvem | Arquitetura definida em [arquitetura_nuvem.md](arquitetura_nuvem.md); falta hospedar com a conta da equipe |
| Autenticação (JWT) | Prevista no plano técnico para a fase 2 |
| Revisão da política de alertas | Alerta baixo informativo, alerta preditivo de transbordo |
