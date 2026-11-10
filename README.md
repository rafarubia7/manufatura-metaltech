# MetalTech Monitoramento Industrial

Monitoramento de uma esteira de triagem de peças com sensores de distância, indutivo e capacitivo: API REST em Java/Spring Boot e pipeline de Ciência de Dados em Python.

## Objetivo

Coletar, armazenar, analisar e disponibilizar as leituras dos sensores para:

- classificar peças (metálica / não metálica);
- monitorar o nível do reservatório;
- gerar alertas operacionais.

## Stack

- **Back-end:** Java 17, Spring Boot 3.2, Spring Data JPA, PostgreSQL 15, Flyway, Springdoc OpenAPI, H2 (testes), Maven Wrapper
- **Ciência de Dados:** Python 3.11+, pandas, numpy, scipy, matplotlib, requests
- **Infra local:** Docker Compose

## Estrutura

```text
.
├── backend/                     API REST (Spring Boot)
│   ├── src/main/java/...        domain · application · api · infrastructure
│   ├── src/main/resources/      application.yml, migrações Flyway
│   ├── src/test/                testes unitários, de controller e de integração
│   └── Dockerfile
├── dados/                       Ciência de Dados (CIDA)
│   ├── gerar_dataset.py         simulação da esteira (seed fixa)
│   ├── analise_estatistica.py   estatística, outliers e gráficos
│   ├── enviar_para_api.py       POST das leituras na API + validação
│   ├── leituras_simuladas.csv   dataset (~10 mil leituras, 7 dias)
│   ├── eventos_simulados.csv    anomalias injetadas (verdade-terreno)
│   ├── graficos/                gráficos técnicos (PNG)
│   └── resultados/              estatísticas, outliers e validação
├── docs/
│   ├── guia_execucao.md         passo a passo e status do projeto
│   └── relatorio_ciencia_dados.md  relatório técnico final da CIDA
└── docker-compose.yml           PostgreSQL (+ back-end opcional)
```

## Execução rápida

```powershell
docker compose up -d postgres                 # banco
cd backend; .\mvnw.cmd test                   # 36 testes
.\mvnw.cmd spring-boot:run                    # API em http://localhost:8080

cd ..\dados
python -m venv .venv; .venv\Scripts\pip install -r requirements.txt
.venv\Scripts\python gerar_dataset.py
.venv\Scripts\python analise_estatistica.py
.venv\Scripts\python enviar_para_api.py       # POST real + validação 22/22
```

Detalhes e solução de problemas em [docs/guia_execucao.md](docs/guia_execucao.md).

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/leituras` | Registra uma leitura (201). O status do reservatório é recalculado pelo back-end |
| GET | `/api/v1/leituras` | Lista paginada. Filtros: `inicio`, `fim`, `tipoPeca`, `statusReservatorio` |
| GET | `/api/v1/leituras/{id}` | Busca uma leitura |
| GET | `/api/v1/leituras/alertas` | Alertas não resolvidos |
| PUT | `/api/v1/leituras/alertas/{id}/resolver` | Resolve um alerta (204; 404 se não existir) |
| GET | `/api/v1/leituras/estatisticas` | Indicadores do período (`inicio`/`fim`) ou de todo o histórico |

A listagem paginada responde com `conteudo`, `paginacao`, `totalElementos`, `totalPaginas`, `primeira`, `ultima` e `quantidadeElementos`. Os parâmetros de paginação seguem o padrão do Spring (`page`, `size`, `sort=dataHora,asc`). Erros seguem o formato `{dataHora, codigoErro, mensagem, caminho, detalhes}`.

Documentação interativa: http://localhost:8080/swagger-ui.html

## Regras de negócio

- **Status do reservatório:**
  - baixo: < 20%;
  - normal: 20–80%;
  - alto: > 80%;
  - crítico: ≥ 95%.
- **Alertas de reservatório:** um alerta ativo por tipo e dispositivo. É resolvido automaticamente quando a condição deixa de valer.
- **Conflito de sensores (peça `AMBAS`):** sempre gera alerta `CONTAGEM_ANOMALA`.
- **Estatísticas:** quartis por interpolação linear e desvio padrão amostral, as mesmas convenções do pandas.

## Ciência de Dados

O dataset é gerado por uma simulação orientada a eventos, com:

- turnos e lotes de produção;
- intensidade dos sensores decaindo com a distância;
- reservatório enchendo gradativamente e esvaziado por comporta;
- gargalos, deriva de sensor, falhas de conexão e esvaziamentos bruscos.

Resultados, gráficos e conclusões estão em [docs/relatorio_ciencia_dados.md](docs/relatorio_ciencia_dados.md).

## Status

- Back-end funcional e validado contra PostgreSQL (36 testes automatizados).
- Integração CIDA → API validada: 9.965 leituras via POST, 22/22 indicadores conferidos.
- Relatório técnico de Ciência de Dados concluído.
- Próximas etapas: front-end, nuvem e realidade aumentada (ver guia de execução).
