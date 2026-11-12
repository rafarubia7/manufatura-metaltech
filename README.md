# MetalTech Monitoramento Industrial

Monitoramento de uma esteira de triagem de peças com sensores de distância, indutivo e capacitivo. O projeto inclui uma API REST em Java/Spring Boot e uma base de pipeline de Ciência de Dados em Python para geração, análise e validação das leituras.

## Objetivo

Coletar, armazenar, analisar e disponibilizar as leituras dos sensores para:

- classificar peças (metálica / não metálica);
- monitorar o nível do reservatório;
- gerar alertas operacionais;
- alimentar dashboards e análise técnica.

## Stack

- Back-end: Java 17, Spring Boot 3.2, Spring Data JPA, MySQL 8.0, Flyway, Springdoc OpenAPI, H2 (testes), Maven Wrapper
- Ciência de Dados: Python 3.11+, pandas, numpy, scipy, matplotlib, requests
- Infra local: Docker Compose com MySQL

## Estado atual do projeto

### Back-end
Status: funcional e validado em ambiente local com MySQL.

Concluído:
- API REST estruturada em Java/Spring Boot
- entidades, DTOs, repositórios e serviços baseados em domínio
- lógica de negócio para leitura de sensores e alertas
- cálculo do status do reservatório e regras de deduplicação
- estatísticas operacionais e endpoints de consulta
- testes automatizados do backend com sucesso

Pendente de refinamento:
- revisão final de documentação e onboarding
- consolidar ambiente de execução e operacionalização
- melhorar observabilidade e robustez de produção

### Ciência de Dados
Status: funcional para dados simulados, pronto para análise e integração com API.

Concluído:
- geração de dataset sintético
- análise estatística exploratória
- extração de métricas operacionais
- detecção de padrões e outliers
- geração de gráficos e resultados em arquivos estruturados

Pendente:
- adaptar o pipeline para dados reais industriais
- validar a camada de conversão e normalização de payloads reais
- concluir relatório técnico final com foco em uso real

### Front-end e próximas etapas
Status: não implementado nesta fase.

Ainda pendente:
- dashboard web
- visualização de alertas e estatísticas
- integrações front-end → API
- deploy/infra de nuvem
- realidade aumentada

## Estrutura do repositório

```text
.
├── backend/                     API REST (Spring Boot)
│   ├── src/main/java/...        domain · application · api · infrastructure
│   ├── src/main/resources/      application.yml, Flyway e configs
│   ├── src/test/                testes unitários e de integração
│   └── Dockerfile
├── dados/                       Ciência de Dados (CIDA)
│   ├── gerar_dataset.py         geração do dataset simulado
│   ├── analise_estatistica.py   estatísticas, outliers e gráficos
│   ├── enviar_para_api.py       envia o dataset simulado para a API e valida API x pandas
│   ├── script_ponte_iolink.py   ponte IO-Link (HEX) para a API
│   ├── leituras_simuladas.csv  dataset principal
│   ├── eventos_simulados.csv   eventos/anomalias
│   ├── graficos/               arquivos gráficos gerados
│   └── resultados/             JSON, CSV, resumo e validação
├── docs/
│   ├── guia_execucao.md        guia de execução do projeto
│   ├── guia_frontend.md        guia de integração do front-end
│   ├── relatorio_ciencia_dados.md  relatório técnico da CIDA
│   └── roadmap_projeto.md      roadmap da etapa atual e próximos passos
├── docker-compose.yml           infraestrutura local do MySQL
└── README.md
```

## Execução rápida

### 0. Credenciais

```powershell
Copy-Item .env.example .env
```

Edite o `.env` e defina as senhas. O `.env` não vai para o Git.

### 1. Banco de dados

```powershell
cd <pasta-do-projeto>
docker compose up -d mysql
docker compose ps
```

Aguarde o status `healthy` e confirme a porta do host `3307`.

### 2. Back-end

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

A API fica disponível em:

- Swagger: http://localhost:8080/swagger-ui.html
- Base da API: http://localhost:8080/api/v1

Se a porta 8080 estiver ocupada:

```powershell
cd backend
$env:SERVER_PORT = "8081"
.\mvnw.cmd spring-boot:run
```

### 3. Python / CIDA

```powershell
cd dados
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt
.venv\Scripts\python gerar_dataset.py
.venv\Scripts\python analise_estatistica.py
.venv\Scripts\python enviar_para_api.py
```

## Endpoints principais

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/leituras` | Registra uma leitura de sensores |
| GET | `/api/v1/leituras` | Lista leituras com paginação e filtros |
| GET | `/api/v1/leituras/{id}` | Busca leitura por id |
| GET | `/api/v1/leituras/alertas` | Lista alertas pendentes |
| PUT | `/api/v1/leituras/alertas/{id}/resolver` | Resolve um alerta |
| GET | `/api/v1/leituras/estatisticas` | Retorna indicadores operacionais |
| GET | `/api/v1/esteira/status` | Estado atual da esteira (último nível, contagens e alertas ativos) |

Documentação interativa:
- http://localhost:8080/swagger-ui.html

## Regras de negócio

- Status do reservatório:
  - baixo: < 20%
  - normal: 20–80%
  - alto: > 80%
  - crítico: ≥ 95%
- Alertas automáticos são gerados pela regra de negócio do backend
- Quando a condição de alerta deixa de existir, o alerta pode ser resolvido automaticamente
- Conflitos entre sensores são considerados anomalias e devem gerar alertas correspondentes

## Ciência de Dados

O pipeline de CIDA foi estruturado para:

- gerar um dataset reproduzível e consistente
- analisar a operação da esteira e o comportamento do reservatório
- identificar padrões, anomalias e outliers
- validar o contrato da API com o dataset simulado

A análise técnica e os relatórios encontram-se em:

- [docs/relatorio_ciencia_dados.md](docs/relatorio_ciencia_dados.md)

## Status final da etapa atual

### Concluído
- backend funcional com MySQL real
- testes automatizados do backend executando com sucesso
- pipeline de dados simulados validado
- integração CIDA → API validação operacional
- documentação de uso e integração organizada

### Pendência de continuidade
- front-end/dashboard
- nuvem/deploy
- autenticação e observabilidade
- adaptação da CIDA para dados reais industriais
- refinamento final da documentação acadêmica e operacional

## Caminho recomendado para a próxima etapa

1. Validar o backend local com MySQL em funcionamento
2. Validar a CIDA com dados simulados e a API
3. Concluir a documentação operacional e onboarding
4. Desenvolver o front-end consumindo a API REST
5. Planejar a adaptação para dados reais do processo industrial

## Observações importantes

- Cobertura de testes medida com JaCoCo (`cd backend; .\mvnw.cmd verify`, relatório em `backend/target/site/jacoco/index.html`): **90% das linhas**; o build falha abaixo de 70%. O teste de integração com MySQL usa Testcontainers e precisa do Docker ativo.
- O projeto usa MySQL por padrão na versão atual; PostgreSQL deixou de ser a referência principal da execução local.
- O Docker é usado para o banco e não para o front-end.
- Para outros PCs da rede, o front-end deve usar o IP do PC que roda o backend, e não `localhost`.

Mais detalhes e instruções em:
- [docs/guia_execucao.md](docs/guia_execucao.md)
- [docs/guia_frontend.md](docs/guia_frontend.md)
- [docs/roadmap_projeto.md](docs/roadmap_projeto.md)
