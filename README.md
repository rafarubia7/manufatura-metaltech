# MetalTech Monitoramento Industrial

Monitoramento de uma esteira de triagem de peças, feito no **Projeto Interdisciplinar II** ([enunciado](docs/projeto.md)). Sensores IO-Link classificam as peças e medem o reservatório; um gateway envia as leituras a uma API Spring Boot, consumida pelo dashboard React e pela análise de dados.

O [app de Realidade Aumentada](https://rafarubia7.github.io/manufatura-metaltech/realidade-aumentada/) reconhece a esteira pela câmera e exibe contagem e nível com **dados simulados**, pois a rede do SENAI é inacessível ao celular ([detalhes](docs/arquitetura_nuvem.md#27-isolamento-da-rede-do-laboratório)).

<p align="center">
  <a href="https://rafarubia7.github.io/manufatura-metaltech/realidade-aumentada/">
    <img src="docs/img/qrcode-ra.png" alt="QR code para abrir o app de Realidade Aumentada no celular" width="160">
  </a>
</p>

## Entregáveis

| Entregável | Onde está |
|---|---|
| Back-end (API REST) | [backend/](backend/) · [contrato da API](backend/README.md#contrato-da-api-v1) |
| Front-end (React) | [frontend/](frontend/) |
| Automação Industrial | [automacao/](automacao/) |
| Realidade Aumentada | [realidade-aumentada/](realidade-aumentada/) |
| Ciência de Dados | [relatório](docs/relatorio_ciencia_dados.md) · [dados/](dados/) |
| Arquitetura de nuvem | [docs/arquitetura_nuvem.md](docs/arquitetura_nuvem.md) |

**Ressalvas:** o sensor de nível (X01) tem defeito físico ([detalhes](automacao/README.md#problema-conhecido-porta-x01-com-io-link-code-530)); a `X-API-Key` existe mas está desativada na rede isolada do laboratório; o R.A usa dados simulados.

## Stack

Java 17 · Spring Boot 3.2 · MySQL 8 · Flyway · React 19 · Vite · Python 3 · A-Frame + MindAR · Docker Compose

## Execução rápida

Requer Docker Desktop:

```powershell
Copy-Item .env.example .env      # defina as senhas
docker compose up -d --build
```

Dashboard em http://localhost:3000 · API em http://localhost:8080/api/v1 (Swagger em `/swagger-ui.html`).

Cada módulo tem seu próprio README com execução local e solução de problemas.

## Qualidade

72 testes automatizados, 90,8% de cobertura de linhas (JaCoCo, mínimo de 70% no build) e migrações versionadas com Flyway.
