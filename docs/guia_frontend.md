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
- O front-end em outro PC deve chamar o back-end pelo IP do computador que está executando o Spring Boot.

Exemplo:

- Backend PC: `10.110.12.43`
- Backend porta padrão: `8080`
- Base da API: `http://10.110.12.43:8080`

Se a porta 8080 estiver ocupada, o backend pode subir na porta 8081. Nesse caso:

- Base da API: `http://10.110.12.43:8081`

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

- `http://10.110.12.43:8080/swagger-ui.html`

Se a porta for 8081:

- `http://10.110.12.43:8081/swagger-ui.html`

---

## 4. Como configurar a URL da API no front-end

No front-end, o ideal é ter uma variável de configuração.

### Exemplo em JavaScript puro

```js
const API_URL = "http://10.110.12.43:8080";
```

### Exemplo em React/Vite

Arquivo `.env`:

```env
VITE_API_URL=http://10.110.12.43:8080
```

No código:

```js
const API_URL = import.meta.env.VITE_API_URL;
```

---

## 5. Endpoints principais do front-end

### 5.1 Listar leituras

```http
GET http://10.110.12.43:8080/api/v1/leituras?size=20
```

### 5.2 Listar alertas

```http
GET http://10.110.12.43:8080/api/v1/leituras/alertas
```

### 5.3 Obter estatísticas

```http
GET http://10.110.12.43:8080/api/v1/leituras/estatisticas
```

### 5.4 Enviar nova leitura

```http
POST http://10.110.12.43:8080/api/v1/leituras
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
PUT http://10.110.12.43:8080/api/v1/leituras/alertas/1/resolver
```

---

## 6. Exemplos de chamadas no front-end

### Exemplo: carregar estatísticas

```js
const API_URL = "http://10.110.12.43:8080";

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
- o front-end usa o IP do backend, e não `localhost`

---

## 10. O que normalmente dá errado

### Problema: o front-end usa localhost

Errado:

```js
const API_URL = "http://localhost:8080";
```

Certo:

```js
const API_URL = "http://10.110.12.43:8080";
```

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
.
\mvnw.cmd spring-boot:run
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

- apontar para o IP do PC do backend
- consumir os endpoints REST da API
- mostrar os dados em tela
- atender a regra de acesso via CORS

O endereço base, no cenário atual, é:

```text
http://10.110.12.43:8080
```

Exemplo completo:

```text
http://10.110.12.43:8080/swagger-ui.html
http://10.110.12.43:8080/api/v1/leituras
http://10.110.12.43:8080/api/v1/leituras/alertas
http://10.110.12.43:8080/api/v1/leituras/estatisticas
```

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
