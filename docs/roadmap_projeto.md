# Plano de Entrega — MetalTech

Checklist de fechamento do projeto, seguindo o documento oficial ([projeto.md](projeto.md)). Cada item cita a seção que o exige. Situação em **12/11/2026**.

Legenda: ✅ pronto · ⏳ depende da equipe · ⚠️ pronto com ressalva

## 1. Entregáveis (seção 7)

| Entregável | Situação | Onde |
|---|---|---|
| Repositório Git com back-end e front-end | ✅ (falta organizar os commits, ver seção 4) | `backend/`, `frontend/` |
| Firmware da camada de Automação | ✅ | `automacao/gateway.py` + [documentacao_circuito.md](documentacao_circuito.md) |
| Aplicativo de Realidade Aumentada | ⏳ a ser anexado e integrado | consome `GET /api/v1/esteira/status` |
| Relatório de Ciência de Dados | ✅ | [relatorio_ciencia_dados.md](relatorio_ciencia_dados.md) |
| Documento de arquitetura de nuvem | ✅ | [arquitetura_nuvem.md](arquitetura_nuvem.md) |
| Apresentação final | ⏳ | roteiro na seção 3 |

## 2. Requisitos por unidade curricular (seções 5 e 8)

### Automação Industrial (5.1)
- ✅ Sensores de distância, indutivo e capacitivo lidos pelo mestre IO-Link
- ✅ Classificação metálica / não metálica combinando indutivo e capacitivo
- ✅ Envio HTTP para o back-end, com fila e reenvio se a API cair
- ✅ Documentação do circuito (modelos lidos do equipamento)
- ⚠️ **Confiabilidade do nível:** a porta X01 (O5D150) falha com `code 530` e o nível pula entre 0% e 95%. É um problema físico. Alinhe o sensor, revise o cabo e recalibre `vazio_raw`/`cheio_raw` (ver [documentacao_circuito.md](documentacao_circuito.md), seção 5). **Resolver antes da apresentação.** Hoje o ruído gera alertas falsos de enchimento rápido.

### Ciência de Dados (5.2)
- ✅ Média, mediana, **moda**, desvio padrão e quartis do nível
- ✅ Estatística descritiva da contagem de peças por tipo (peças por hora de metálicas e não metálicas)
- ✅ Gráficos de linha, barras, dispersão, histograma e boxplot
- ✅ Outliers e critério de alerta (enchimento > 5 %/min), implementado no back-end
- Opcional: rodar `analise_estatistica.py --fonte api` com as leituras reais, depois que o sensor de distância estiver estável

### Computação em Nuvem (5.3)
- ✅ Modelo de serviço justificado (PaaS), segurança, disponibilidade, escalabilidade e custos
- ⏳ **Hospedagem real:** o critério de avaliação inclui a "disponibilidade do serviço hospedado". Siga o passo a passo da seção 8 de [arquitetura_nuvem.md](arquitetura_nuvem.md) com a conta de nuvem da equipe. Sem conta, apresente a arquitetura e rode localmente com `docker compose up -d --build`.

### Desenvolvimento Back-end (5.4)
- ✅ Orientação a objetos:
  - herança e polimorfismo em `Sensor` → `SensorIndutivo`/`SensorCapacitivo`;
  - encapsulamento em `Usuario` e `LeituraSensores`.
- ✅ Esteira, leituras, peças classificadas e **usuários** representados no modelo
- ✅ CRUD com banco: leituras (`/leituras`) e usuários (`/usuarios`), com migrations Flyway V1–V3
- ✅ Endpoints documentados: Swagger e [contrato_api.md](contrato_api.md)
- ✅ 72 testes automatizados, 90,8% de cobertura de linhas, teste com MySQL real e health check (`/actuator/health`)

### Realidade Aumentada (5.5)
- ✅ Back-end pronto: o `/esteira/status` traz contagens por tipo, nível, status e `exibirAlerta`. O `exibirAlerta` fica ligado com o reservatório alto ou crítico, **ou** quando o enchimento está fora do padrão da CIDA.
- ⏳ App: reconhecer o marcador, mostrar contagem metálica/não metálica e nível, e destacar quando `exibirAlerta = true`

### Frameworks Front-end (5.6)
- ✅ Consome a API em tempo quase real (polling de 4 s e 10 s)
- ✅ Gráficos e indicadores da CIDA (média, mediana, desvio, quartis, critério de alerta)
- ✅ Responsivo, com componentes, props e estado, e **rotas** (React Router)
- ✅ Framework CSS (Bootstrap 5)

## 3. Roteiro sugerido da apresentação (fluxo da seção 6)

1. **Problema:** contagem manual na esteira (1 min).
2. **Automação:** passar peças reais e mostrar o log do gateway (`classificada: METALICA ...`).
3. **Back-end:** Swagger, `POST` recebido, classificação e alertas recalculados, testes e cobertura.
4. **Nuvem:** diagrama e justificativa do PaaS. Se estiver hospedado, mostrar a URL pública.
5. **Ciência de Dados:** gráficos do relatório e critério de 5 %/min, que vira o alerta "Enchimento rápido".
6. **Front-end:** dashboard atualizando sozinho enquanto as peças passam. Mostrar as 4 rotas.
7. **RA:** apontar para o marcador e mostrar os mesmos números e o alerta.
8. **Demonstração de robustez:** derrubar a API por alguns segundos, passar peças, subir a API de novo e mostrar que nada se perdeu.

## 4. Fechamento do repositório

1. Com a API parada, rodar `cd backend; .\mvnw.cmd clean verify`. Precisa terminar em `BUILD SUCCESS`.
2. Rodar `cd frontend; npm run lint; npm run build`.
3. Organizar os commits por área (back-end, front-end, automação, CIDA, documentação) e criar a branch/tag de entrega.
4. Antes da apresentação, opcional: limpar o banco de testes para começar a demonstração do zero (`docker compose down -v` e `docker compose up -d mysql` apagam **todos** os dados).

## 5. Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Sensor de distância instável (`code 530`) | Nível errado e alertas falsos na demonstração | Recalibrar e alinhar antes. Em último caso, demonstrar o nível com o dataset da CIDA |
| Rede do laboratório (mestre em 10.110.12.41) | Gateway sem dados | Testar `--sondar` antes de começar |
| RA entregue sem testar com a API | Integração falha ao vivo | Testar contra o `/esteira/status` real pelo menos 1 dia antes |
| Sem conta de nuvem | Critério de "serviço hospedado" incompleto | Mostrar a arquitetura e o `docker compose` com a mesma imagem que iria para a nuvem |
