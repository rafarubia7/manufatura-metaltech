# Ciência de Dados — MetalTech

Scripts da análise estatística da esteira. A interpretação dos resultados, os gráficos comentados e as conclusões estão no [Relatório de Ciência de Dados](../docs/relatorio_ciencia_dados.md).

| Arquivo | O que faz |
|---|---|
| `gerar_dataset.py` | Gera o dataset sintético de 7 dias (`leituras_simuladas.csv`, ~10 mil leituras) e as janelas das anomalias injetadas (`eventos_simulados.csv`). Com a mesma semente (42), o arquivo sai idêntico |
| `analise_estatistica.py` | Estatística descritiva, outliers, critério de alerta e os 10 gráficos. Lê o CSV ou as leituras gravadas na API (`--fonte api`) |
| `enviar_para_api.py` | Envia o dataset para a API por `POST /api/v1/leituras` e confere os indicadores da API contra o pandas |
| `graficos/` | Gráficos usados no relatório |
| `resultados/` | `estatisticas.json` (todos os indicadores), `outliers.csv` (leituras sinalizadas) e `validacao_integracao.json` (validação API × pandas: 23/23) |

## Ambiente

Python 3.11 ou mais recente.

```powershell
cd dados
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
```

No PowerShell, escreva sempre o `.\` antes de `.venv`. Sem ele, aparece o erro `Não foi possível carregar o módulo '.venv'`. Também não é preciso ativar o venv: chame `.\.venv\Scripts\python` direto.

## Gerar e analisar

```powershell
.\.venv\Scripts\python gerar_dataset.py                       # parâmetros: --dias, --seed, --saida
.\.venv\Scripts\python analise_estatistica.py                 # sobre o dataset sintético (CSV)
.\.venv\Scripts\python analise_estatistica.py --fonte api     # sobre as leituras reais gravadas na API
```

Os gráficos saem em `graficos/` e os resultados em `resultados/`. Com `--fonte api`, o banco precisa ter leituras: rode antes o gateway ([automacao/](../automacao/)).

> **Limitação conhecida:** `analise_estatistica.py --fonte api` falha com `ValueError: Cannot set a DataFrame with multiple columns to the single column detectores` quando nenhuma leitura é marcada como outlier. Isso acontece com poucas leituras no banco.

## Em contêiner (sem instalar Python)

O serviço `dados` do `compose.yaml` roda os scripts sob demanda. A pasta `dados/` é montada no contêiner, então gráficos e resultados saem direto aqui (sobrescrevendo os arquivos versionados).

```powershell
# na raiz do repositório
docker compose run --rm dados                                       # analise_estatistica.py sobre o CSV
docker compose run --rm dados gerar_dataset.py
docker compose run --rm dados analise_estatistica.py --fonte api --url http://backend:8080
```

Dentro da rede do compose, a API é `http://backend:8080`, e não `localhost`. O `API_KEY` do `.env` chega ao contêiner como `METALTECH_API_KEY`.

## Validar a API com o dataset (opcional)

```powershell
.\.venv\Scripts\python enviar_para_api.py
```

Esperado: 100% de HTTP 201 e `Validação API x pandas: 23/23 checagens aprovadas`.

> **Cuidado:** este script **grava as ~10 mil leituras sintéticas no banco** e exige o banco vazio. Elas aparecem no dashboard misturadas às leituras reais do gateway. Use só para validar a API e depois limpe com `docker compose down -v` (apaga os dados), ou rode contra um banco de teste. Opções: `--limite 500` (envio parcial), `--atraso 1` (uma leitura por segundo) e `--url` (outro endereço). Se o back-end tiver `API_KEY`, defina antes `$env:METALTECH_API_KEY = "<a mesma chave>"`.

## Solução de problemas

| Sintoma | Causa provável | Solução |
|---|---|---|
| `Python was not found` ou abre a Microsoft Store | Python não instalado | Instale pelo python.org, marcando **Add python.exe to PATH** |
| `a execução de scripts foi desabilitada` | Política de execução do PowerShell ao ativar o venv | Não ative: chame `.\.venv\Scripts\python` direto |
| `--fonte api`: erro ou sem dados | Banco vazio | Rode o gateway e passe peças |
| `API indisponível` no `enviar_para_api.py` | Back-end parado | Suba a API ([backend/README.md](../backend/README.md)) |
| Dashboard com milhares de leituras que não são dos sensores | Rodou `enviar_para_api.py` no banco real | `docker compose down -v` e suba o MySQL de novo |
| Acentos quebrados no console | Codificação do terminal | Os scripts já forçam UTF-8; use o Windows Terminal |
