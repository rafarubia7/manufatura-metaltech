# Front-end — Dashboard MetalTech

Dashboard web da plataforma: contagem de peças por tipo, nível do reservatório, gráficos e indicadores da Ciência de Dados, ocorrências e gestão de usuários. Consome a API REST do back-end ([contrato](../backend/README.md#contrato-da-api-v1)).

Stack: React 19, React Router 7, Bootstrap 5, Recharts 3, Vite 8 e oxlint.

## Executar

Node.js 20.19+ ou 22.12+. Com a API rodando em `http://localhost:8080`:

```powershell
cd frontend
npm install
npm run dev               # http://localhost:5173
npm run dev -- --host     # para abrir de outro computador da rede
```

Qualidade e build:

```powershell
npm run lint              # oxlint
npm run build             # gera dist/
npm run preview           # serve o dist/ localmente
```

Em contêiner: na raiz do repositório, `docker compose up -d --build` sobe o dashboard em http://localhost:3000.

## Arquitetura

```text
Navegador ──► dashboard (React + Vite) ──HTTP/JSON──► API REST :8080 ──► MySQL
```

- O dashboard só consome a API REST. Ele não acessa o banco e não grava leituras: quem grava é o gateway da automação.
- No servidor, roda no contêiner `frontend` do `compose.yaml`. O build do Vite é servido pelo Nginx na porta **3000**.

## Endereço da API

Todas as chamadas HTTP ficam em [src/services/api.js](src/services/api.js):

```js
const API_URL = import.meta.env.VITE_API_URL || `${window.location.protocol}//${window.location.hostname}:8080`;
```

- **Sem `VITE_API_URL`** (padrão): usa o host pelo qual a página foi aberta, na porta 8080. No próprio PC é `localhost`; em outro computador da rede, é o IP do servidor. Não é preciso editar IP quando ele muda.
- **Com `VITE_API_URL`**: usa o valor definido. Defina em `frontend/.env.local`, que não vai para o Git. Exemplo: `VITE_API_URL=http://localhost:8081`. Depois, reinicie o `npm run dev`.
- **No Docker**, o valor entra no build (`ARG VITE_API_URL` no [Dockerfile](Dockerfile)). Vazio, vale o padrão acima.

## Estrutura

```text
frontend/
├── index.html               página base do Vite
├── src/
│   ├── main.jsx             ponto de entrada: BrowserRouter, Bootstrap e estilos
│   ├── App.jsx              cabeçalho, estado global (conexão, alertas, tema) e rotas
│   ├── pages/               uma tela por rota: PainelAoVivo, GraficosHistorico, TabelaManutencoes, GestaoUsuarios
│   ├── components/          componentes reutilizáveis: NavBar
│   ├── services/api.js      todas as chamadas HTTP à API
│   └── styles/index.css     tema claro/escuro e estilos próprios (sobre o Bootstrap)
├── nginx.conf               servidor do build no Docker (fallback das rotas)
├── Dockerfile               build com Node e publicação com Nginx
├── vite.config.js
└── .oxlintrc.json           regras do lint
```

## Rotas e telas

O roteamento usa **React Router** (`BrowserRouter` em `main.jsx`, `Routes` em `App.jsx`). Cada rota é uma tela em `src/pages/`:

| Rota | Tela | Conteúdo | Atualização |
|---|---|---|---|
| `/` | `PainelAoVivo` | Cards de metálicas, não metálicas, desconhecidas (nenhum sensor acionado) e ocorrências ativas; nível do reservatório com status e marcas de 20/80/95%; últimas 5 leituras com data e hora | a cada 4 s |
| `/analise` | `GraficosHistorico` | Evolução do nível (com as linhas de 80% e 95%), distribuição por tipo de peça e resumo estatístico: média, mediana, desvio, mínimo, máximo e quartis | a cada 10 s |
| `/ocorrencias` | `TabelaManutencoes` | Alertas pendentes com data, tipo legível e botão "Marcar como resolvido" | a cada 10 s e botão Atualizar |
| `/usuarios` | `GestaoUsuarios` | CRUD de usuários (formulário + tabela, ativar/desativar, excluir) | ao salvar |
| qualquer outra | — | redireciona para `/` | — |

- `NavBar` recebe o número de alertas por prop (`qtdAlertas`) e mostra o contador na rota Ocorrências.
- `App` guarda o estado global (conexão com a API, alertas e tema) e o repassa às rotas por props. Exemplo: `TabelaManutencoes` recebe `aoAlterarAlertas` para atualizar o contador assim que um alerta é resolvido.
- `App` verifica a conexão a cada 5 s e mostra "Conectado" ou "Sem conexão" no cabeçalho.
- Estilo: Bootstrap 5 (grid responsivo, tabelas, formulários, badges) mais as variáveis de tema em `styles/index.css`. O tema escuro também vale para os componentes do Bootstrap (`data-bs-theme`).

## Endpoints consumidos

| Função em `api.js` | Endpoint | Usada em |
|---|---|---|
| `buscarEstatisticas` | `GET /api/v1/leituras/estatisticas` | `App` (status da conexão), `PainelAoVivo`, `GraficosHistorico` |
| `buscarLeituras` | `GET /api/v1/leituras?page=&size=` | `PainelAoVivo` (últimas 5), `GraficosHistorico` (série do nível) |
| `buscarAlertas` | `GET /api/v1/leituras/alertas` | `App` (contador), `TabelaManutencoes` |
| `resolverAlerta` | `PUT /api/v1/leituras/alertas/{id}/resolver` | `TabelaManutencoes` |
| `listarUsuarios`, `criarUsuario`, `atualizarUsuario`, `excluirUsuario` | `GET/POST/PUT/DELETE /api/v1/usuarios` | `GestaoUsuarios` |

As respostas passam por funções de normalização em `api.js` (por exemplo, `normalizarEstatisticas`), então os componentes não dependem de variações de nome de campo. O dashboard não usa o `GET /api/v1/esteira/status`, que foi criado para o R.A.

## Docker e Nginx

- O [Dockerfile](Dockerfile) tem duas etapas: build com Node 22 (`npm ci` + `npm run build`) e publicação com Nginx 1.27.
- As rotas são do navegador. Por isso o [nginx.conf](nginx.conf) devolve o `index.html` para qualquer caminho desconhecido (`/analise`, `/ocorrencias`, `/usuarios`). O `npm run dev` e o `npm run preview` já fazem isso sozinhos.
- Arquivos em `/assets/` (com hash no nome) ficam em cache por 30 dias. O `index.html` nunca fica em cache.

## Rede, firewall e CORS

- **CORS:** a API libera as origens de `CORS_ORIGENS` (padrão `*`). Em produção, use só o domínio do dashboard.
- **Outro computador:** abra o dashboard pelo **IP do servidor** (`http://<ip>:3000`, ou `npm run dev -- --host` em desenvolvimento). O dashboard reaproveita esse IP para chamar a API. Abrir por `localhost` em outro PC não funciona.
- **Firewall:** no Windows do servidor, libere as portas 3000 e 8080 para a rede privada.

## Solução de problemas

| Sintoma | Causa provável | Solução |
|---|---|---|
| Cabeçalho mostra "Sem conexão" | API fora do ar ou porta errada | `docker compose ps` (backend `healthy`) ou suba o back-end. Se a porta não for 8080, defina `VITE_API_URL` |
| Funciona no servidor, mas não em outro PC | Página aberta por `localhost`, ou firewall | Abra pelo IP do servidor e libere as portas 3000 e 8080 |
| `/analise` dá 404 ao recarregar | Servidor estático sem fallback do SPA | Use o contêiner (o `nginx.conf` já trata) ou `npm run preview` |
| Painel vazio ("Aguardando envio de leituras...") | Banco sem leituras | Ligue o gateway (bancada) ou envie o dataset da Ciência de Dados (`dados/scripts/enviar_para_api.py`) |
| Porta 8080 ocupada | Outro processo usando a porta | `netstat -ano \| findstr :8080`, depois suba a API em outra porta (`SERVER_PORT`) e ajuste `VITE_API_URL` |

## Relação com a Realidade Aumentada

O dashboard e o R.A são as duas interfaces previstas na seção 6 do [enunciado](../docs/projeto.md). O R.A está publicado em https://rafarubia7.github.io/manufatura-metaltech/realidade-aumentada/ e usa dados simulados, porque o celular não tem acesso à rede do SENAI, onde ficam a bancada e a API.

**Pendência do front-end:** incluir na `NavBar` um link externo "Realidade Aumentada" (`<a href={import.meta.env.VITE_RA_URL} target="_blank" rel="noopener noreferrer">`, com a URL em `VITE_RA_URL`). Opcional: mostrar o QR code ([docs/img/qrcode-ra.png](../docs/img/qrcode-ra.png), copiado para `frontend/public/`) no Painel, para abrir no celular durante a apresentação.
