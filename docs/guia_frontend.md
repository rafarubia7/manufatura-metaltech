# Guia de integração do Front-End com a API MetalTech

Este documento explica exatamente o que o front-end precisa fazer para consumir a API do back-end.

## 1. Entendimento da arquitetura

O projeto está dividido em:

- Back-end: Java + Spring Boot
- Banco de dados: MySQL em Docker
- Front-end: aplicação cliente que apenas consome a API REST

Importante:

- O Docker usado neste projeto é para o banco MySQL, não para o front-end.
- O back-end é executado localmente no PC que roda o Java.
- No próprio PC do back-end, o endereço é `localhost`. Em outro PC, o front-end chama o back-end pelo IP do computador que executa o Spring Boot.
- O front-end descobre o endereço sozinho: sem configuração, ele usa o mesmo host pelo qual a página foi aberta, na porta 8080. Por isso não é preciso editar o IP quando ele muda.

Exemplo:

- Backend porta padrão: `8080`
- Base da API no próprio PC: `http://localhost:8080`
- Base da API a partir de outro PC: `http://<IP-do-PC-do-backend>:8080` (descubra o IP com `ipconfig`)

Se a porta 8080 estiver ocupada, o backend pode subir na porta 8081. Nesse caso:

- Base da API: `http://localhost:8081`. Nesse caso defina `VITE_API_URL=http://localhost:8081` em `frontend/.env.local` e ajuste o `api_url` do gateway

---

## 2. O que o front-end não precisa fazer

O front-end não precisa:

- subir o MySQL
- usar Docker
- acessar o banco diretamente
- rodar Java
- mexer em `application.yml`
- mexer em Flyway
- mexer em JPA/Hibernate
- conhecer a estrutura interna do banco

O front-end só precisa:

- consumir a API REST
- enviar dados
- receber dados em JSON
- exibir os dados na interface

---

## 3. O que o front-end precisa consumir

A API principal está em:

- `GET /api/v1/leituras`
- `GET /api/v1/leituras/alertas`
- `GET /api/v1/leituras/estatisticas`
- `POST /api/v1/leituras`
- `PUT /api/v1/leituras/alertas/{id}/resolver`

A documentação completa está no Swagger:

- `http://localhost:8080/swagger-ui.html`

Se a porta for 8081:

- `http://localhost:8081/swagger-ui.html`

---

## 4. Como configurar a URL da API no front-end

O front-end deste projeto (`frontend/src/services/api.js`) já resolve o endereço sozinho:

```js
const API_URL = import.meta.env.VITE_API_URL || `${window.location.protocol}//${window.location.hostname}:8080`;
```

- Sem `VITE_API_URL`: usa o host em que a página foi aberta, na porta 8080 (`localhost` no próprio PC, o IP do PC do back-end quando aberto por outro computador).
- Com `VITE_API_URL`: usa o valor definido. Para não alterar arquivo versionado, defina em `frontend/.env.local`:

```env
VITE_API_URL=http://localhost:8081
```

Para outros PCs abrirem o dashboard, suba o Vite expondo a rede: `npm run dev -- --host`.

---

## 5. Endpoints principais do front-end

### 5.1 Listar leituras

```http
GET http://localhost:8080/api/v1/leituras?size=20
```

### 5.2 Listar alertas

```http
GET http://localhost:8080/api/v1/leituras/alertas
```

### 5.3 Obter estatísticas

```http
GET http://localhost:8080/api/v1/leituras/estatisticas
```

### 5.4 Enviar nova leitura

```http
POST http://localhost:8080/api/v1/leituras
Content-Type: application/json
```

Exemplo de payload:

```json
{
  "timestamp": "2026-11-03T10:00:00Z",
  "sensorDistancia": {
    "valor": 9.5,
    "unidade": "cm",
    "status": "OK"
  },
  "sensorIndutivo": {
    "detectouMetal": true,
    "intensidade": 0.85,
    "status": "OK"
  },
  "sensorCapacitivo": {
    "detectouNaoMetal": false,
    "intensidade": 0.1,
    "status": "OK"
  },
  "peca": {
    "id": "peca-1",
    "tipo": "METALICA",
    "timestampDetectado": "2026-11-03T10:00:00Z"
  },
  "nivelReservatorio": {
    "nivelAtual": 97,
    "capacidadeMaxima": 100,
    "percentual": 97,
    "status": "NORMAL"
  },
  "dispositivoId": "ESP32-001",
  "versaoFirmware": "1.0.0"
}
```

### 5.5 Resolver alerta

```http
PUT http://localhost:8080/api/v1/leituras/alertas/1/resolver
```

---

## 6. Exemplos de chamadas no front-end

### Exemplo: carregar estatísticas

```js
const API_URL = "http://localhost:8080";

async function carregarEstatisticas() {
  const resposta = await fetch(`${API_URL}/api/v1/leituras/estatisticas`);
  const dados = await resposta.json();
  console.log(dados);
}
```

### Exemplo: listar leituras

```js
async function carregarLeituras() {
  const resposta = await fetch(`${API_URL}/api/v1/leituras?size=20`);
  const dados = await resposta.json();
  console.log(dados);
}
```

### Exemplo: listar alertas

```js
async function carregarAlertas() {
  const resposta = await fetch(`${API_URL}/api/v1/leituras/alertas`);
  const dados = await resposta.json();
  console.log(dados);
}
```

### Exemplo: enviar leitura

```js
async function enviarLeitura() {
  const payload = {
    timestamp: "2026-11-03T10:00:00Z",
    sensorDistancia: {
      valor: 9.5,
      unidade: "cm",
      status: "OK"
    },
    sensorIndutivo: {
      detectouMetal: true,
      intensidade: 0.85,
      status: "OK"
    },
    sensorCapacitivo: {
      detectouNaoMetal: false,
      intensidade: 0.1,
      status: "OK"
    },
    peca: {
      id: "peca-1",
      tipo: "METALICA",
      timestampDetectado: "2026-11-03T10:00:00Z"
    },
    nivelReservatorio: {
      nivelAtual: 97,
      capacidadeMaxima: 100,
      percentual: 97,
      status: "NORMAL"
    },
    dispositivoId: "ESP32-001",
    versaoFirmware: "1.0.0"
  };

  const resposta = await fetch(`${API_URL}/api/v1/leituras`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });

  const dados = await resposta.json();
  console.log(dados);
}
```

---

## 7. O que o front-end precisa mostrar na tela

O dashboard pode conter os seguintes blocos:

- Reservatório atual
- Percentual atual
- Status do reservatório
- Última leitura recebida
- Quantidade de alertas pendentes
- Lista de alertas
- Gráficos de nível e status
- Tabela de leituras recentes

---

## 8. Estrutura mínima recomendada do front-end

### Tela 1 — Dashboard principal

- cards com indicadores principais
- status do reservatório
- quantidade de alertas
- última leitura

### Tela 2 — Leituras

- tabela com leituras recentes
- filtro por período
- filtro por tipo de peça
- filtro por status do reservatório

### Tela 3 — Alertas

- lista de alertas ativos
- botão para marcar como resolvido
- exibir tipo do alerta e timestamp

### Tela 4 — Estatísticas

- gráfico ou cards com contagem por tipo de peça
- indicadores globais
- métricas operacionais

---

## 9. CORS e acesso em rede local

No projeto, o back-end já tem configuração CORS em `WebConfig.java`, e isso ajuda o front-end em desenvolvimento a acessar a API sem bloqueios.

Mesmo assim, se o front-end estiver em outro PC, é necessário garantir que:

- o backend está rodando no PC correto
- a porta 8080 ou 8081 está acessível
- o firewall do PC do backend não está bloqueando a porta
- o front-end foi aberto pelo IP do PC do backend (ele reaproveita esse host para chamar a API); abrir por `localhost` em outro PC não funciona

---

## 10. O que normalmente dá errado

### Problema: o front-end fixa `localhost`

Em outro PC, `localhost` aponta para o próprio computador e não para o back-end. Não escreva o endereço fixo no código: use o padrão de `api.js` (host da página + porta 8080) ou defina `VITE_API_URL`.

### Problema: porta bloqueada

Se o front-end não conseguir acessar a API, verifique:

```powershell
netstat -ano | findstr :8080
```

Também pode ser necessário liberar a porta 8080 no firewall do Windows.

### Problema: backend não está rodando

Verifique se o Spring Boot está ativo:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

ou com porta alternativa:

```powershell
cd backend
$env:SERVER_PORT = "8081"
.\mvnw.cmd spring-boot:run
```

---

## 11. Resumo prático

O front-end precisa apenas:

- apontar para a API (automático: mesmo host da página, porta 8080)
- consumir os endpoints REST da API
- mostrar os dados em tela
- atender a regra de acesso via CORS

O endereço base, no próprio PC do back-end, é:

```text
http://localhost:8080
```

Exemplo completo:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/api/v1/leituras
http://localhost:8080/api/v1/leituras/alertas
http://localhost:8080/api/v1/leituras/estatisticas
```

Em outro PC, troque `localhost` pelo IP do PC do back-end.

---

## 12. Recomendação final

O front-end deve ser desenvolvido usando o contrato da API como fonte da verdade. A melhor prática é:

1. abrir o Swagger no backend;
2. validar os endpoints e os campos JSON;
3. integrar o front-end por IP do backend;
4. montar primeiro o dashboard de estatísticas e alertas;
5. depois construir a tabela de leituras e filtros.

Se o front-end for em outro PC, ele nunca deve apontar para `localhost`.
Ele deve apontar para o IP do PC que está rodando o back-end.

## Estado atual da esteira

`GET /api/v1/esteira/status` devolve tudo que o painel principal e a RA precisam, em uma chamada:

```json
{
  "ultimaLeitura": "2026-11-08T23:58:41Z",
  "dispositivoId": "ESP32-001",
  "nivelReservatorioPercentual": 82.5,
  "statusReservatorio": "ALERTA_ALTO",
  "totalMetalicas": 6000,
  "totalNaoMetalicas": 3303,
  "totalDesconhecidas": 613,
  "totalAmbas": 50,
  "alertasAtivos": 2
}
```

Responde `404` (`NAO_ENCONTRADO`) enquanto não existir nenhuma leitura. Use polling a cada poucos segundos.
