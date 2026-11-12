# Roadmap do Projeto MetalTech

## 1. Visão geral

Este documento consolida o estado atual da etapa de desenvolvimento, os principais pontos pendentes e a ordem correta para completar o projeto sem dispersão.

## 2. Estado atual

### Concluído
- Base do backend em Java/Spring Boot funcional
- API REST com endpoints principais operacionais
- Persistência com MySQL e Flyway
- Geração de alertas e estatísticas de negócio
- Testes automatizados do backend executando
- Pipeline de dados simulados em Python
- Validação inicial da integração CIDA → API

### Em andamento
- refinamento final da documentação
- padronização de ambiente e instruções de execução
- organização da estrutura do repositório
- preparação para a próxima etapa: front-end e entrega final

### Pendente
- dashboard web
- integração front-end com API
- deploy e nuvem
- autenticação/segurança
- observabilidade
- adaptação da CIDA para dados reais industriais

## 3. Objetivo da fase atual

Fechar a base funcional e deixar o projeto pronto para as próximas frentes sem ambiguidade, inconsistência de documentação ou dependência de contexto informal.

## 4. Ordem recomendada de execução

### Fase 1 — Alinhar a base de execução
1. confirmar JDK 17
2. iniciar MySQL via Docker
3. validar que o backend sobe
4. confirmar Swagger e endpoints
5. validar `docker-compose.yml` e a URL de conexão

### Fase 2 — Alinhar documentação e onboarding
1. revisar README
2. revisar guia de execução
3. revisar guia do front-end
4. remover trechos antigos com PostgreSQL no fluxo principal
5. definir um único padrão para uso local e rede

### Fase 3 — Revisão da estrutura do repositório
1. separar documentação ativa de histórico
2. separar artefatos gerados de código
3. garantir que pastas sigam nomes claros e consistentes
4. remover ou ignorar arquivos que não devem ir para versionamento

### Fase 4 — Validar CIDA e API
1. validar geração do dataset
2. validar análise estatística
3. validar envio para API
4. confirmar que os resultados são coerentes com a regra de negócio

### Fase 5 — Preparar a próxima etapa
1. front-end/dashboard
2. integrações de operação e visualização
3. instruções para rede e autenticação
4. planejamento de deploy e nuvem

## 5. Critérios de sucesso da fase atual

- Backend funcional em ambiente local com MySQL
- README e guias consistentes com o projeto real
- API documentada e integrada ao processo de operação
- CIDA validada com dados simulados
- Repositório organizado e pronto para continuidade

## 6. Riscos principais

- divergência entre documentação e configuração real
- uso de `localhost` no front-end quando a aplicação está em outra máquina
- banco e schema sem alinhamento documental claro
- mistura de artefatos gerados e arquivos de origem
- ausência de uma estratégia formal para dados reais industriais

## 7. Próximo passo imediato

Antes de iniciar o desenvolvimento do front-end, a equipe deve:

1. fechar documentação
2. validar backend
3. validar pipeline CIDA
4. confirmar URL de acesso em rede local
5. preparar interface seguindo o contrato da API

Esse é o caminho mais seguro e com menor risco para a etapa seguinte.
