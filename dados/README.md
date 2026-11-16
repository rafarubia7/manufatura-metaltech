# Ciência de Dados — MetalTech

Scripts da análise estatística da esteira. A interpretação dos resultados, os gráficos comentados e as conclusões estão no [Relatório de Ciência de Dados](../docs/relatorio_ciencia_dados.md).

```text
dados/
├── scripts/
│   ├── gerar_dataset.py         gera o dataset sintético (semente 42: sai sempre idêntico)
│   ├── analise_estatistica.py   estatística descritiva, outliers, critério de alerta e gráficos (CSV ou API)
│   └── enviar_para_api.py       envia o dataset à API e confere os indicadores da API contra o pandas
├── dataset/
│   ├── leituras_simuladas.csv   7 dias de operação simulada (9.965 leituras)
│   └── eventos_simulados.csv    janelas das anomalias injetadas (verdade-terreno dos detectores)
├── graficos/                    os 10 gráficos usados no relatório
├── resultados/                  estatisticas.json, outliers.csv e validacao_integracao.json (API × pandas: 23/23)
├── requirements.txt
└── Dockerfile                   imagem do serviço "dados" do compose.yaml
```

Os scripts acham as pastas sozinhos, a partir da própria localização. Dá para rodá-los de qualquer diretório.

## Ambiente

Python 3.11 ou mais recente.

```powershell
cd dados
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
```

No PowerShell, escreva sempre o `.\` antes de `.venv`. Sem ele, aparece o erro `Não foi possível carregar o módulo '.venv'`. Também não é preciso ativar o venv: chame `.\.venv\Scripts\python` direto.

## Gerar e analisar

Na pasta `dados/`:

```powershell
.\.venv\Scripts\python scripts\gerar_dataset.py                     # parâmetros: --dias, --seed, --saida
.\.venv\Scripts\python scripts\analise_estatistica.py               # sobre o dataset sintético (CSV)
.\.venv\Scripts\python scripts\analise_estatistica.py --fonte api   # sobre as leituras reais gravadas na API
```

Os gráficos saem em `graficos/` e os resultados em `resultados/`. A análise também grava um `resumo.txt`, que fica fora do Git porque repete o relatório. Com `--fonte api`, o banco precisa ter leituras: rode antes o gateway ([automacao/](../automacao/)).

> **Limitação conhecida:** `analise_estatistica.py --fonte api` falha com `ValueError: Cannot set a DataFrame with multiple columns to the single column detectores` quando nenhuma leitura é marcada como outlier. Isso acontece com poucas leituras no banco.

## Em contêiner (sem instalar Python)

O serviço `dados` do `compose.yaml` roda os scripts sob demanda, já dentro de `scripts/`. A pasta `dados/` é montada no contêiner, então gráficos e resultados saem direto aqui (sobrescrevendo os arquivos versionados).

```powershell
# na raiz do repositório
docker compose run --rm dados                                       # analise_estatistica.py sobre o CSV
docker compose run --rm dados gerar_dataset.py
docker compose run --rm dados analise_estatistica.py --fonte api --url http://backend:8080
```

Dentro da rede do compose, a API é `http://backend:8080`, e não `localhost`. O `API_KEY` do `.env` chega ao contêiner como `METALTECH_API_KEY`.

## Validar a API com o dataset (opcional)

```powershell
.\.venv\Scripts\python scripts\enviar_para_api.py
```

Esperado: 100% de HTTP 201 e `Validação API x pandas: 23/23 checagens aprovadas`.

> **Cuidado:** este script **grava as ~10 mil leituras sintéticas no banco** e exige o banco vazio. Elas aparecem no dashboard misturadas às leituras reais do gateway. Use só para validar a API e depois limpe com `docker compose down -v` (apaga os dados), ou rode contra um banco de teste. Opções: `--limite 500` (envio parcial), `--atraso 1` (uma leitura por segundo), `--csv` (outro arquivo) e `--url` (outro endereço). Se o back-end tiver `API_KEY`, defina antes `$env:METALTECH_API_KEY = "<a mesma chave>"`.

## Solução de problemas

| Sintoma | Causa provável | Solução |
|---|---|---|
| `Python was not found` ou abre a Microsoft Store | Python não instalado | Instale pelo python.org, marcando **Add python.exe to PATH** |
| `a execução de scripts foi desabilitada` | Política de execução do PowerShell ao ativar o venv | Não ative: chame `.\.venv\Scripts\python` direto |
| `--fonte api`: erro ou sem dados | Banco vazio | Rode o gateway e passe peças |
| `API indisponível` no `enviar_para_api.py` | Back-end parado | Suba a API ([backend/README.md](../backend/README.md)) |
| Dashboard com milhares de leituras que não são dos sensores | Rodou `enviar_para_api.py` no banco real | `docker compose down -v` e suba o MySQL de novo |
| Acentos quebrados no console | Codificação do terminal | A análise e o envio já forçam UTF-8; use o Windows Terminal |
