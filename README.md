# MetalTech Monitoramento Industrial

Projeto de monitoramento de esteira de triagem com API REST em Java/Spring Boot e pipeline de Ciência de Dados.

## Objetivo

Coletar, armazenar, analisar e disponibilizar leituras de sensores industriais para classificação de peças, monitoramento do nível do reservatório e geração de alertas operacionais.

## Stack

- Java 17
- Spring Boot 3.2.0
- Spring Data JPA
- PostgreSQL
- Flyway
- H2 para testes
- Maven
- Python + pandas + numpy

## Estrutura

```text
.
├── backend/
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── mvnw
├── dados/
│   ├── gerar_dataset.py
│   ├── analise_inicial.txt
│   └── leituras_simuladas.csv
├── README.md
├── projeto_interdisciplinar.md
├── plano_acao_tecnico.md
└── .gitignore
```

## Requisitos

- Java 17
- Maven 3.9+
- PostgreSQL 15+ (opcional para execução local)

## Execução do backend

```powershell
cd backend
./mvnw test
./mvnw spring-boot:run
```

## Endpoints principais

- POST `/api/v1/leituras`
- GET `/api/v1/leituras`
- GET `/api/v1/leituras/{id}`
- GET `/api/v1/leituras/alertas`
- PUT `/api/v1/leituras/alertas/{id}/resolver`
- GET `/api/v1/leituras/estatisticas`

## Swagger

A documentação OpenAPI fica disponível ao subir a aplicação em:

```text
http://localhost:8080/swagger-ui.html
```

## CIDA

A pasta `dados/` contém o dataset sintético da operação e os arquivos iniciais de análise.

Fluxo:

1. `dados/gerar_dataset.py` gera os dados simulados
2. `dados/leituras_simuladas.csv` armazena os registros
3. `dados/analise_inicial.txt` registra a análise descritiva inicial

## Observações

- O projeto foi padronizado em português para o contexto de negócio.
- Nomes da tecnologia e da convenção Java/Spring continuam em inglês por padrão da stack.
- A base backend foi validada com testes automatizados.

## Status

- Backend funcional
- API REST estruturada
- Alertas automatizados
- Estatísticas de operação disponíveis
- Base de dados sintética e análise inicial concluídas