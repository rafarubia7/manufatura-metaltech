"""
Análise estatística da operação da esteira MetalTech.

Fonte: CSV local (padrão) ou leituras persistidas na API (--fonte api).
Saídas: dados/resultados/ (estatisticas.json, outliers.csv, resumo.txt) e dados/graficos/*.png.

Uso:
    python analise_estatistica.py
    python analise_estatistica.py --fonte api --url http://localhost:8080
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import timedelta, timezone
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.dates as mdates
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy import stats

PASTA = Path(__file__).resolve().parent
PASTA_RESULTADOS = PASTA / "resultados"
PASTA_GRAFICOS = PASTA / "graficos"
FUSO = timezone(timedelta(hours=-3))

LIMITE_BAIXO, LIMITE_ALTO, LIMITE_CRITICO = 20.0, 80.0, 95.0
TAXA_ENCHIMENTO_ANOMALA = 5.0
TAXA_ESVAZIAMENTO_ANOMALA = -3.0
INTENSIDADE_FALHA = 0.10
JANELA_TAXA_MIN = 3
JANELA_VAZAO = "10min"
Z_ROBUSTO_LIMITE = 3.5

# Três primeiros slots da paleta validada para daltonismo + cores reservadas de status.
AZUL, LARANJA, VERDE_AGUA = "#2a78d6", "#eb6834", "#1baf7a"
CINZA_SERIE = "#898781"
COR_STATUS = {"NORMAL": "#0ca30c", "ALERTA_BAIXO": "#898781", "ALERTA_ALTO": "#fab219", "CRITICO": "#d03b3b"}
TINTA, TINTA_SECUNDARIA, GRADE, EIXO = "#0b0b0b", "#52514e", "#e1e0d9", "#c3c2b7"
ORDEM_TIPOS = ["METALICA", "NAO_METALICA", "DESCONHECIDA", "AMBAS"]
ORDEM_STATUS = ["ALERTA_BAIXO", "NORMAL", "ALERTA_ALTO", "CRITICO"]
NOME_TIPO = {"METALICA": "Metálica", "NAO_METALICA": "Não metálica", "DESCONHECIDA": "Desconhecida", "AMBAS": "Ambas (conflito)"}
NOME_STATUS = {"ALERTA_BAIXO": "Alerta baixo (<20%)", "NORMAL": "Normal (20-80%)",
               "ALERTA_ALTO": "Alerta alto (>80%)", "CRITICO": "Crítico (≥95%)"}


def normalizar_colunas_numericas(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    colunas_numericas = [
        "distancia_cm",
        "indutivo_intensidade",
        "capacitivo_intensidade",
        "nivel_reservatorio_percentual",
    ]
    for coluna in colunas_numericas:
        if coluna in df.columns:
            df[coluna] = pd.to_numeric(df[coluna], errors="coerce")

    if "tipo_peca" in df.columns:
        df["tipo_peca"] = df["tipo_peca"].replace({"": pd.NA, None: pd.NA}).fillna("DESCONHECIDA")
    if "status_reservatorio" in df.columns:
        df["status_reservatorio"] = df["status_reservatorio"].replace({"": pd.NA, None: pd.NA}).fillna("NORMAL")
    return df


def carregar_csv(caminho: Path) -> pd.DataFrame:
    return normalizar_colunas_numericas(pd.read_csv(caminho))


def carregar_api(url: str) -> pd.DataFrame:
    import requests

    linhas, pagina = [], 0
    while True:
        resposta = requests.get(f"{url}/api/v1/leituras",
                                params={"page": pagina, "size": 1000, "sort": "dataHora,asc"}, timeout=60)
        resposta.raise_for_status()
        corpo = resposta.json()
        for leitura in corpo["conteudo"]:
            linhas.append({
                "timestamp": leitura["timestamp"],
                "distancia_cm": leitura["sensorDistancia"]["valor"],
                "indutivo_detectou_metal": leitura["sensorIndutivo"]["detectouMetal"],
                "indutivo_intensidade": leitura["sensorIndutivo"]["intensidade"],
                "capacitivo_detectou_nao_metal": leitura["sensorCapacitivo"]["detectouNaoMetal"],
                "capacitivo_intensidade": leitura["sensorCapacitivo"]["intensidade"],
                "tipo_peca": leitura["peca"]["tipo"],
                "nivel_reservatorio_percentual": leitura["nivelReservatorio"]["percentual"],
                "status_reservatorio": leitura["nivelReservatorio"]["status"],
                "dispositivo_id": leitura["dispositivoId"],
            })
        if corpo["ultima"]:
            break
        pagina += 1
    return normalizar_colunas_numericas(pd.DataFrame(linhas))


def turno_por_hora(hora) -> np.ndarray:
    return np.select([(hora >= 6) & (hora < 14), (hora >= 14) & (hora < 22)], ["T1", "T2"], "T3")


def preparar(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["timestamp"] = pd.to_datetime(df["timestamp"], utc=True, format="ISO8601").dt.tz_convert(FUSO)
    df = df.sort_values("timestamp", kind="stable").reset_index(drop=True)
    if "turno" not in df:
        df["turno"] = turno_por_hora(df["timestamp"].dt.hour)
    # Peso de cada leitura no tempo (até a próxima), para medir tempo em cada status.
    df["duracao_min"] = df["timestamp"].diff().shift(-1).dt.total_seconds().div(60).fillna(0)
    df["taxa_nivel_pct_min"] = taxa_variacao(df)
    return df


def taxa_variacao(df: pd.DataFrame) -> pd.Series:
    """%/min contra a primeira leitura dentro da janela, para que uma parada não dilua a rajada seguinte."""
    base = df[["timestamp", "nivel_reservatorio_percentual"]].rename(
        columns={"timestamp": "instante_anterior", "nivel_reservatorio_percentual": "nivel_anterior"})
    alvo = pd.DataFrame({"referencia": df["timestamp"] - pd.Timedelta(minutes=JANELA_TAXA_MIN)})
    anterior = pd.merge_asof(alvo, base, left_on="referencia", right_on="instante_anterior", direction="forward")
    minutos = (df["timestamp"] - anterior["instante_anterior"]).dt.total_seconds() / 60
    taxa = (df["nivel_reservatorio_percentual"] - anterior["nivel_anterior"]) / minutos
    return taxa.where(minutos >= 1).round(3)


def descrever(serie: pd.Series) -> dict:
    valores = serie.dropna()
    q1, q2, q3 = valores.quantile([0.25, 0.5, 0.75])
    return {
        "n": int(valores.size), "media": round(valores.mean(), 3), "mediana": round(q2, 3),
        "desvio_padrao": round(valores.std(), 3), "minimo": round(valores.min(), 3), "q1": round(q1, 3),
        "q3": round(q3, 3), "iqr": round(q3 - q1, 3), "maximo": round(valores.max(), 3),
        "assimetria": round(float(stats.skew(valores)), 3), "curtose": round(float(stats.kurtosis(valores)), 3),
        "cv_pct": round(100 * valores.std() / valores.mean(), 2) if valores.mean() else None,
    }


def vazao(df: pd.DataFrame, frequencia: str) -> pd.DataFrame:
    serie = df.set_index("timestamp").resample(frequencia).size().rename("pecas").to_frame()
    serie["turno"] = turno_por_hora(serie.index.hour)
    return serie


def ciclos_esvaziamento(df: pd.DataFrame, queda_minima: float = 25.0, retomada: float = 5.0) -> dict:
    """Ciclos pico -> vale com histerese: robusto ao ruído de peça a peça."""
    instantes, niveis = df["timestamp"].to_numpy(), df["nivel_reservatorio_percentual"].to_numpy()
    ciclos, enchendo = [], True
    indice_pico = indice_vale = 0
    for i in range(len(niveis)):
        if enchendo:
            if niveis[i] >= niveis[indice_pico]:
                indice_pico = i
            elif niveis[indice_pico] - niveis[i] >= queda_minima:
                enchendo, indice_vale = False, i
        else:
            if niveis[i] <= niveis[indice_vale]:
                indice_vale = i
            elif niveis[i] - niveis[indice_vale] >= retomada:
                ciclos.append((indice_pico, indice_vale))
                enchendo, indice_pico = True, i
    minutos = lambda a, b: (pd.Timestamp(instantes[b]) - pd.Timestamp(instantes[a])).total_seconds() / 60
    esvaziamentos = [minutos(pico, vale) for pico, vale in ciclos]
    enchimentos = [minutos(vale, proximo_pico) for (_, vale), (proximo_pico, _) in zip(ciclos, ciclos[1:])]
    picos = [float(niveis[pico]) for pico, _ in ciclos]
    vales = [float(niveis[vale]) for _, vale in ciclos]
    dias = (df["timestamp"].iloc[-1] - df["timestamp"].iloc[0]).total_seconds() / 86400
    return {
        "quantidade": len(ciclos),
        "por_dia": round(len(ciclos) / max(dias, 1), 2),
        "duracao_media_esvaziamento_min": round(float(np.mean(esvaziamentos)), 1) if esvaziamentos else None,
        "tempo_medio_enchimento_min": round(float(np.mean(enchimentos)), 1) if enchimentos else None,
        "tempo_mediano_enchimento_min": round(float(np.median(enchimentos)), 1) if enchimentos else None,
        "nivel_medio_no_inicio_do_esvaziamento": round(float(np.mean(picos)), 2) if picos else None,
        "nivel_medio_ao_fim_do_esvaziamento": round(float(np.mean(vales)), 2) if vales else None,
        "esvaziamentos_iniciados_acima_de_95pct": int(sum(1 for p in picos if p >= LIMITE_CRITICO)),
        "esvaziamentos_iniciados_abaixo_de_80pct": int(sum(1 for p in picos if p <= LIMITE_ALTO)),
    }


LIMITE_ENCHIMENTO_PCT_MIN = 5.0   # mesmo limite do back-end (R1 da CIDA)
JANELA_TAXA_S = 180.0             # janela anterior de 3 min
JANELA_MINIMA_S = 60.0            # exige ao menos 1 min entre a leitura-base e a atual


def simular_alertas(df: pd.DataFrame) -> dict:
    """Mesma regra do backend: um alerta de reservatório ativo por tipo; cada AMBAS gera alerta;
    ENCHIMENTO_RAPIDO abre um alerta por episódio (taxa > 5 %/min na janela anterior de 3 min)."""
    mapa = {"ALERTA_BAIXO": "RESERVATORIO_BAIXO", "ALERTA_ALTO": "RESERVATORIO_ALTO", "CRITICO": "RESERVATORIO_CRITICO"}
    contagem = {"RESERVATORIO_BAIXO": 0, "RESERVATORIO_ALTO": 0, "RESERVATORIO_CRITICO": 0,
                "CONTAGEM_ANOMALA": 0, "ENCHIMENTO_RAPIDO": 0}
    ativo, aberto_em, duracoes = None, None, {tipo: [] for tipo in mapa.values()}
    segundos = np.array([t.timestamp() for t in df["timestamp"]])
    niveis = df["nivel_reservatorio_percentual"].to_numpy(dtype=float)
    enchendo = False
    for i, (instante, status, tipo_peca) in enumerate(zip(df["timestamp"], df["status_reservatorio"], df["tipo_peca"])):
        atual = mapa.get(status)
        if ativo and ativo != atual:
            duracoes[ativo].append((instante - aberto_em).total_seconds() / 60)
            ativo = None
        if atual and ativo is None:
            ativo, aberto_em = atual, instante
            contagem[atual] += 1
        if tipo_peca == "AMBAS":
            contagem["CONTAGEM_ANOMALA"] += 1
        # Primeira leitura em [t-3min, t): estritamente anterior, como a consulta do back-end.
        primeiro = np.searchsorted(segundos, segundos[i] - JANELA_TAXA_S, side="left")
        limite = np.searchsorted(segundos, segundos[i], side="left")
        taxa = None
        if primeiro < limite and segundos[i] - segundos[primeiro] >= JANELA_MINIMA_S:
            taxa = (niveis[i] - niveis[primeiro]) / ((segundos[i] - segundos[primeiro]) / 60.0)
        if taxa is not None and taxa > LIMITE_ENCHIMENTO_PCT_MIN:
            if not enchendo:
                contagem["ENCHIMENTO_RAPIDO"] += 1
            enchendo = True
        else:
            enchendo = False
    dias = (df["timestamp"].iloc[-1] - df["timestamp"].iloc[0]).total_seconds() / 86400
    return {
        "alertas_por_tipo": contagem,
        "alertas_ativos_ao_final": 1 if ativo else 0,
        "enchimento_ativo_ao_final": 1 if enchendo else 0,
        "tipo_ativo_ao_final": ativo,
        "duracao_media_alerta_min": {tipo: (round(float(np.mean(v)), 1) if v else None) for tipo, v in duracoes.items()},
        "alertas_por_dia": round(sum(contagem.values()) / max(dias, 1), 1),
    }


def detectar_outliers(df: pd.DataFrame, vazao_10min: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, dict]:
    sinais = pd.DataFrame(index=df.index)
    sinais["R1_enchimento_acima_5pct_min"] = df["taxa_nivel_pct_min"] > TAXA_ENCHIMENTO_ANOMALA
    sinais["R2_esvaziamento_acima_3pct_min"] = df["taxa_nivel_pct_min"] < TAXA_ESVAZIAMENTO_ANOMALA
    # Peça não metálica já tem indutivo baixo; falha de conexão exige os DOIS sensores sem sinal.
    sinais["R3_sinal_nulo_nos_sensores"] = ((df["indutivo_intensidade"] < INTENSIDADE_FALHA)
                                            & (df["capacitivo_intensidade"] < INTENSIDADE_FALHA))
    sinais["R4_conflito_ambas"] = df["tipo_peca"] == "AMBAS"

    taxa = df["taxa_nivel_pct_min"]
    mediana = taxa.median()
    mad = (taxa - mediana).abs().median() * 1.4826
    df["z_robusto_taxa"] = ((taxa - mediana) / mad).round(2)
    sinais["Z_taxa_nivel_robusto"] = df["z_robusto_taxa"].abs() > Z_ROBUSTO_LIMITE

    limites = {}
    vazao_10min = vazao_10min.copy()
    vazao_10min["outlier_iqr"] = ""
    for turno, grupo in vazao_10min.groupby("turno"):
        q1, q3 = grupo["pecas"].quantile([0.25, 0.75])
        inferior, superior = q1 - 1.5 * (q3 - q1), q3 + 1.5 * (q3 - q1)
        limites[turno] = {"q1": float(q1), "q3": float(q3), "limite_inferior": float(inferior), "limite_superior": float(superior)}
        vazao_10min.loc[grupo.index[grupo["pecas"] < max(inferior, 0.5)], "outlier_iqr"] = "BAIXO"
        vazao_10min.loc[grupo.index[grupo["pecas"] > superior], "outlier_iqr"] = "ALTO"
    janela = df["timestamp"].dt.floor(JANELA_VAZAO)
    sinais["IQR_vazao_alta"] = janela.map(vazao_10min["outlier_iqr"]).eq("ALTO").values

    algum = sinais.any(axis=1)
    df_outliers = df[algum].copy()
    df_outliers["detectores"] = sinais[algum].apply(lambda linha: ";".join(linha.index[linha]), axis=1)
    parametros = {"mediana_taxa": round(float(mediana), 4), "mad_escalado": round(float(mad), 4),
                  "limites_iqr_vazao_10min": limites}
    return sinais, vazao_10min, {"parametros": parametros, "df_outliers": df_outliers}


def avaliar_detectores(df: pd.DataFrame, sinais: pd.DataFrame, vazao_10min: pd.DataFrame, eventos: pd.DataFrame | None) -> dict:
    """Compara os detectores com a verdade-terreno da simulação (só existe na fonte CSV)."""
    if "evento_simulado" not in df:
        return {}
    alvo = {
        "R1_enchimento_acima_5pct_min": "RAJADA_POS_GARGALO",
        "R2_esvaziamento_acima_3pct_min": "ESVAZIAMENTO_BRUSCO",
        "R3_sinal_nulo_nos_sensores": "FALHA_CONEXAO",
        "R4_conflito_ambas": "DERIVA_CAPACITIVO",
        "IQR_vazao_alta": "RAJADA_POS_GARGALO",
    }
    episodio = (df["evento_simulado"] != df["evento_simulado"].shift()).cumsum()
    resultado = {}
    for detector, evento in alvo.items():
        sinal, verdade = sinais[detector], df["evento_simulado"] == evento
        verdadeiros_positivos = int((sinal & verdade).sum())
        episodios = episodio[verdade].unique()
        detectados = episodio[verdade & sinal].unique()
        resultado[detector] = {
            "evento_alvo": evento, "leituras_sinalizadas": int(sinal.sum()),
            "precisao_pct": round(100 * verdadeiros_positivos / sinal.sum(), 1) if sinal.sum() else None,
            "episodios": int(len(episodios)), "episodios_detectados": int(len(detectados)),
            "recall_episodios_pct": round(100 * len(detectados) / len(episodios), 1) if len(episodios) else None,
        }

    sinal = sinais["Z_taxa_nivel_robusto"]
    verdade = df["evento_simulado"].isin(["RAJADA_POS_GARGALO", "ESVAZIAMENTO_BRUSCO"])
    resultado["Z_taxa_nivel_robusto"] = {
        "evento_alvo": "RAJADA_POS_GARGALO ou ESVAZIAMENTO_BRUSCO", "leituras_sinalizadas": int(sinal.sum()),
        "precisao_pct": round(100 * (sinal & verdade).sum() / sinal.sum(), 1) if sinal.sum() else None,
        "observacao": "sinaliza também esvaziamentos controlados (variação legítima, porém rara)",
    }

    # Gargalos não geram leituras: são avaliados pelas janelas de vazão baixa.
    if eventos is not None:
        gargalos = [(pd.Timestamp(g["inicio"]).tz_convert(FUSO), pd.Timestamp(g["fim"]).tz_convert(FUSO))
                    for _, g in eventos[eventos["tipo"] == "GARGALO"].iterrows()]
        baixas = vazao_10min.index[vazao_10min["outlier_iqr"] == "BAIXO"]
        largura = pd.Timedelta(JANELA_VAZAO)
        sobrepoe = lambda janela, ini, fim: janela + largura > ini and janela < fim
        detectados = sum(any(sobrepoe(b, ini, fim) for b in baixas) for ini, fim in gargalos)
        janelas_em_gargalo = sum(any(sobrepoe(b, ini, fim) for ini, fim in gargalos) for b in baixas)
        resultado["IQR_vazao_baixa"] = {
            "evento_alvo": "GARGALO", "janelas_sinalizadas": int(len(baixas)),
            "precisao_pct": round(100 * janelas_em_gargalo / len(baixas), 1) if len(baixas) else None,
            "episodios": len(gargalos), "episodios_detectados": detectados,
            "recall_episodios_pct": round(100 * detectados / len(gargalos), 1) if gargalos else None,
        }
    return resultado


def configurar_estilo():
    plt.rcParams.update({
        "figure.dpi": 150, "savefig.dpi": 150, "font.family": ["Segoe UI", "DejaVu Sans", "sans-serif"],
        "font.size": 9.5, "axes.titlesize": 11.5, "axes.titleweight": "bold", "axes.titlelocation": "left",
        "axes.edgecolor": EIXO, "axes.labelcolor": TINTA_SECUNDARIA, "axes.grid": True, "grid.color": GRADE,
        "grid.linewidth": 0.6, "axes.spines.top": False, "axes.spines.right": False,
        "xtick.color": TINTA_SECUNDARIA, "ytick.color": TINTA_SECUNDARIA, "text.color": TINTA, "legend.frameon": False,
        "figure.facecolor": "#fcfcfb", "axes.facecolor": "#fcfcfb", "savefig.facecolor": "#fcfcfb",
        "axes.axisbelow": True,
    })


def linhas_limite(eixo, horizontal=True, rotulos=True):
    for valor, cor, nome in [(LIMITE_BAIXO, COR_STATUS["ALERTA_BAIXO"], "Baixo 20%"),
                             (LIMITE_ALTO, COR_STATUS["ALERTA_ALTO"], "Alto 80%"),
                             (LIMITE_CRITICO, COR_STATUS["CRITICO"], "Crítico 95%")]:
        if horizontal:
            eixo.axhline(valor, color=cor, lw=1, ls="--", zorder=1)
            if rotulos:
                eixo.annotate(nome, xy=(1.0, valor), xycoords=("axes fraction", "data"), xytext=(4, 0),
                              textcoords="offset points", va="center", fontsize=8, color=TINTA_SECUNDARIA)
        else:
            eixo.axvline(valor, color=cor, lw=1, ls="--", zorder=1)
            if rotulos:
                eixo.annotate(nome, xy=(valor, 1.0), xycoords=("data", "axes fraction"), xytext=(0, 3),
                              textcoords="offset points", ha="center", fontsize=8, color=TINTA_SECUNDARIA)


def salvar(figura, nome):
    PASTA_GRAFICOS.mkdir(parents=True, exist_ok=True)
    figura.savefig(PASTA_GRAFICOS / nome, bbox_inches="tight")
    plt.close(figura)


def formatar_eixo_tempo(eixo, formato="%d/%m\n%Hh"):
    eixo.xaxis.set_major_formatter(mdates.DateFormatter(formato, tz=FUSO))


def rotular_barras(eixo, barras, valores):
    for barra, valor in zip(barras, valores):
        eixo.annotate(f"{valor:.1f}%", xy=(barra.get_width(), barra.get_y() + barra.get_height() / 2), xytext=(4, 0),
                      textcoords="offset points", va="center", fontsize=9)


def gerar_graficos(df, vazao_10min, sinais, eventos, resultado):
    configurar_estilo()
    nivel = df["nivel_reservatorio_percentual"]
    gargalos = eventos[eventos["tipo"] == "GARGALO"] if eventos is not None else pd.DataFrame()

    figura, eixo = plt.subplots(figsize=(11, 4))
    for _, e in gargalos.iterrows():
        eixo.axvspan(pd.Timestamp(e["inicio"]), pd.Timestamp(e["fim"]), color=LARANJA, alpha=0.18, lw=0)
    eixo.plot(df["timestamp"], nivel, color=AZUL, lw=0.8)
    linhas_limite(eixo)
    eixo.set_ylim(0, 102)
    eixo.set_ylabel("Nível do reservatório (%)")
    eixo.set_title("Nível do reservatório ao longo da semana")
    if not gargalos.empty:
        eixo.text(0, -0.2, "Faixas laranja: esteira parada por atolamento (gargalo)", transform=eixo.transAxes,
                  fontsize=8, color=TINTA_SECUNDARIA)
    formatar_eixo_tempo(eixo)
    salvar(figura, "01_serie_temporal_nivel_semana.png")

    anomalos = df.loc[df.get("evento_simulado", pd.Series("NORMAL", index=df.index)) != "NORMAL", "timestamp"].dt.date
    dia_alvo = anomalos.mode().iloc[0] if not anomalos.empty else df["timestamp"].dt.date.iloc[0]
    dia = df[df["timestamp"].dt.date == dia_alvo]
    figura, eixo = plt.subplots(figsize=(11, 4))
    eixo.plot(dia["timestamp"], dia["nivel_reservatorio_percentual"], color=AZUL, lw=1.2, label="Nível")
    if "comporta_aberta" in dia:
        eixo.fill_between(dia["timestamp"], 0, 102, where=dia["comporta_aberta"], color=VERDE_AGUA, alpha=0.15, lw=0,
                          label="Comporta de descarga aberta")
    for _, e in gargalos.iterrows():
        inicio = pd.Timestamp(e["inicio"])
        if inicio.date() == dia_alvo:
            eixo.axvspan(inicio, pd.Timestamp(e["fim"]), color=LARANJA, alpha=0.2, lw=0)
            eixo.annotate("gargalo", xy=(inicio, 99), fontsize=8, color=TINTA_SECUNDARIA)
    criticos = dia[dia["status_reservatorio"] == "CRITICO"]
    eixo.scatter(criticos["timestamp"], criticos["nivel_reservatorio_percentual"], s=10, color=COR_STATUS["CRITICO"],
                 zorder=3, label="Leitura em nível crítico")
    linhas_limite(eixo)
    eixo.set_ylim(0, 102)
    eixo.set_ylabel("Nível do reservatório (%)")
    eixo.set_title(f"Ciclos de enchimento e esvaziamento em 24 h ({dia_alvo:%d/%m/%Y})")
    eixo.legend(loc="upper left", bbox_to_anchor=(0, -0.12), ncol=3)
    formatar_eixo_tempo(eixo, "%Hh")
    salvar(figura, "02_serie_temporal_nivel_24h.png")

    contagem = df.set_index("timestamp").groupby([pd.Grouper(freq="1h"), "tipo_peca"]).size().unstack(fill_value=0)
    contagem["OUTRAS"] = contagem.get("DESCONHECIDA", 0) + contagem.get("AMBAS", 0)
    figura, eixo = plt.subplots(figsize=(11, 4))
    for coluna, cor, nome in [("METALICA", AZUL, "Metálica"), ("NAO_METALICA", LARANJA, "Não metálica"),
                              ("OUTRAS", VERDE_AGUA, "Desconhecida + conflito")]:
        eixo.plot(contagem.index, contagem[coluna], color=cor, lw=1.2, label=nome)
    eixo.set_ylabel("Peças por hora")
    eixo.set_title("Vazão horária por tipo de peça (ciclo diário de turnos)")
    eixo.legend(loc="upper left", ncol=3)
    formatar_eixo_tempo(eixo)
    salvar(figura, "03_serie_temporal_vazao_tipo.png")

    figura, eixo = plt.subplots(figsize=(8, 4))
    eixo.hist(nivel, bins=np.arange(0, 102, 2), color=AZUL, edgecolor="#fcfcfb", linewidth=1)
    linhas_limite(eixo, horizontal=False)
    media = resultado["nivel_reservatorio"]["media"]
    eixo.axvline(media, color=TINTA, lw=1)
    eixo.annotate(f"média {media:.1f}%", xy=(media, 0.92), xycoords=("data", "axes fraction"),
                  xytext=(4, 0), textcoords="offset points", fontsize=8)
    eixo.set_xlabel("Nível do reservatório (%)")
    eixo.set_ylabel("Leituras")
    eixo.set_title("Distribuição do nível do reservatório", pad=14)
    salvar(figura, "04_histograma_nivel.png")

    turnos = ["T1", "T2", "T3"]
    for nome, dados, rotulo_y, titulo in [
        ("05_boxplot_nivel_por_turno.png", [df.loc[df["turno"] == t, "nivel_reservatorio_percentual"] for t in turnos],
         "Nível do reservatório (%)", "Nível do reservatório por turno"),
        ("06_boxplot_vazao_por_turno.png", [vazao_10min.loc[vazao_10min["turno"] == t, "pecas"] for t in turnos],
         "Peças a cada 10 min", "Vazão em janelas de 10 min por turno (pontos = outliers IQR)"),
    ]:
        figura, eixo = plt.subplots(figsize=(7, 4))
        eixo.boxplot(dados, tick_labels=["T1 (06-14h)", "T2 (14-22h)", "T3 (22-06h)"], widths=0.45, patch_artist=True,
                     boxprops=dict(facecolor="#cde2fb", edgecolor=AZUL), medianprops=dict(color=TINTA, lw=1.5),
                     whiskerprops=dict(color=AZUL), capprops=dict(color=AZUL),
                     flierprops=dict(marker="o", markersize=4, markerfacecolor=LARANJA, markeredgecolor="#fcfcfb"))
        if "nivel" in nome:
            linhas_limite(eixo)
        eixo.set_ylabel(rotulo_y)
        eixo.set_title(titulo)
        salvar(figura, nome)

    proporcao = df["tipo_peca"].value_counts(normalize=True).reindex(ORDEM_TIPOS, fill_value=0) * 100
    figura, eixo = plt.subplots(figsize=(8, 3.2))
    barras = eixo.barh([NOME_TIPO[t] for t in proporcao.index][::-1], proporcao.values[::-1], color=AZUL, height=0.55)
    rotular_barras(eixo, barras, proporcao.values[::-1])
    eixo.set_xlabel("% das leituras")
    eixo.grid(axis="y", visible=False)
    eixo.set_title("Proporção de peças por tipo classificado")
    salvar(figura, "07_barras_tipo_peca.png")

    status = resultado["status_reservatorio"]
    valores = [status[s]["percentual_tempo"] for s in ORDEM_STATUS]
    figura, eixo = plt.subplots(figsize=(8, 3.2))
    barras = eixo.barh([NOME_STATUS[s] for s in ORDEM_STATUS][::-1], valores[::-1],
                       color=[COR_STATUS[s] for s in ORDEM_STATUS][::-1], height=0.55)
    rotular_barras(eixo, barras, valores[::-1])
    eixo.set_xlabel("% do tempo de operação")
    eixo.grid(axis="y", visible=False)
    eixo.set_title("Tempo do reservatório em cada faixa de status")
    salvar(figura, "08_barras_status_reservatorio.png")

    figura, eixos = plt.subplots(1, 2, figsize=(11, 4), sharey=True)
    metal = df["material_real"] == "METAL" if "material_real" in df else df["tipo_peca"].isin(["METALICA", "AMBAS"])
    paineis = [(eixos[0], df[metal], "indutivo_intensidade", 0.50, "Sensor indutivo - peças metálicas"),
               (eixos[1], df[~metal], "capacitivo_intensidade", 0.45, "Sensor capacitivo - peças não metálicas")]
    for eixo, dados, coluna, limiar, titulo in paineis:
        com_sinal = dados[dados[coluna] >= INTENSIDADE_FALHA]
        sem_sinal = dados[dados[coluna] < INTENSIDADE_FALHA]
        eixo.scatter(com_sinal["distancia_cm"], com_sinal[coluna], s=8, alpha=0.35, color=AZUL, edgecolors="none",
                     label="Leituras")
        eixo.scatter(sem_sinal["distancia_cm"], sem_sinal[coluna], s=12, color=LARANJA, edgecolors="none",
                     label="Sinal < 0,10 (possível falha)")
        eixo.axhline(limiar, color=TINTA_SECUNDARIA, lw=1, ls="--")
        eixo.annotate(f"limiar de detecção {limiar:.2f}", xy=(25, limiar), xytext=(0, 4), textcoords="offset points",
                      ha="right", fontsize=8, color=TINTA_SECUNDARIA)
        eixo.set_xlabel("Distância peça-sensor (cm)")
        eixo.set_title(titulo, fontsize=10.5)
        eixo.legend(loc="upper right")
    eixos[0].set_ylabel("Intensidade normalizada")
    figura.suptitle("Distância x intensidade: o sinal decai com a distância e define o alcance útil",
                    x=0.07, ha="left", fontweight="bold", fontsize=11.5)
    salvar(figura, "09_dispersao_distancia_intensidade.png")

    figura, eixo = plt.subplots(figsize=(11, 4))
    eixo.plot(df["timestamp"], df["taxa_nivel_pct_min"], color=CINZA_SERIE, lw=0.5, label="Taxa de variação (janela 3 min)")
    enchimento = df[sinais["R1_enchimento_acima_5pct_min"]]
    esvaziamento = df[sinais["R2_esvaziamento_acima_3pct_min"]]
    eixo.scatter(enchimento["timestamp"], enchimento["taxa_nivel_pct_min"], s=14, color=LARANJA, zorder=3,
                 label="Enchimento > 5%/min")
    eixo.scatter(esvaziamento["timestamp"], esvaziamento["taxa_nivel_pct_min"], s=14, color=AZUL, zorder=3,
                 label="Esvaziamento > 3%/min")
    eixo.axhline(TAXA_ENCHIMENTO_ANOMALA, color=LARANJA, lw=1, ls="--")
    eixo.axhline(TAXA_ESVAZIAMENTO_ANOMALA, color=AZUL, lw=1, ls="--")
    eixo.set_ylabel("Variação do nível (%/min)")
    eixo.set_title("Taxa de variação do nível e outliers operacionais")
    eixo.legend(loc="upper left", bbox_to_anchor=(0, -0.12), ncol=3)
    formatar_eixo_tempo(eixo)
    salvar(figura, "10_taxa_variacao_outliers.png")


def analisar(df: pd.DataFrame, eventos: pd.DataFrame | None, gerar_figuras: bool = True, sufixo: str = "") -> dict:
    df = preparar(df)
    vazao_hora = vazao(df, "1h")
    vazao_10min = vazao(df, JANELA_VAZAO)
    total = len(df)

    tempo_total = df["duracao_min"].sum()
    status = {}
    for s in ORDEM_STATUS:
        filtro = df["status_reservatorio"] == s
        status[s] = {"leituras": int(filtro.sum()), "percentual_leituras": round(100 * filtro.mean(), 2),
                     "percentual_tempo": round(100 * df.loc[filtro, "duracao_min"].sum() / tempo_total, 2)}

    por_turno = {}
    for turno, grupo in df.groupby("turno"):
        pecas_hora = vazao_hora[vazao_hora["turno"] == turno]["pecas"]
        por_turno[turno] = {
            "leituras": int(len(grupo)),
            "pecas_por_hora": descrever(pecas_hora),
            "percentual_por_tipo": (grupo["tipo_peca"].value_counts(normalize=True) * 100).round(2)
                                   .reindex(ORDEM_TIPOS, fill_value=0).to_dict(),
            "percentual_tempo_critico": round(100 * grupo.loc[grupo["status_reservatorio"] == "CRITICO", "duracao_min"].sum()
                                              / grupo["duracao_min"].sum(), 2),
            "nivel_medio": round(grupo["nivel_reservatorio_percentual"].mean(), 2),
        }

    sinais, vazao_10min, info_outliers = detectar_outliers(df, vazao_10min)
    df_outliers = info_outliers["df_outliers"]
    status_esperado = df["nivel_reservatorio_percentual"].map(
        lambda p: "CRITICO" if p >= 95 else "ALERTA_ALTO" if p > 80 else "ALERTA_BAIXO" if p < 20 else "NORMAL")
    resultado = {
        "periodo": {"inicio": df["timestamp"].iloc[0].isoformat(), "fim": df["timestamp"].iloc[-1].isoformat(),
                    "dias": round((df["timestamp"].iloc[-1] - df["timestamp"].iloc[0]).total_seconds() / 86400, 2)},
        "total_leituras": total,
        "qualidade_dados": {
            "valores_ausentes": int(df[["distancia_cm", "indutivo_intensidade", "capacitivo_intensidade",
                                        "nivel_reservatorio_percentual"]].isna().sum().sum()),
            "timestamps_duplicados": int(df["timestamp"].duplicated().sum()),
            "nivel_fora_de_0_100": int((~df["nivel_reservatorio_percentual"].between(0, 100)).sum()),
            "intensidade_fora_de_0_1": int((~df[["indutivo_intensidade", "capacitivo_intensidade"]].stack().between(0, 1)).sum()),
            "status_inconsistente_com_regra": int((df["status_reservatorio"] != status_esperado).sum()),
            "intervalo_mediano_entre_leituras_s": round(float(df["timestamp"].diff().dt.total_seconds().median()), 1),
            "maior_intervalo_sem_leitura_min": round(float(df["timestamp"].diff().dt.total_seconds().max() / 60), 1),
        },
        "nivel_reservatorio": descrever(df["nivel_reservatorio_percentual"]),
        "distancia_cm": descrever(df["distancia_cm"]),
        "indutivo_intensidade": descrever(df["indutivo_intensidade"]),
        "capacitivo_intensidade": descrever(df["capacitivo_intensidade"]),
        "taxa_variacao_nivel_pct_min": descrever(df["taxa_nivel_pct_min"]),
        "pecas_por_hora": descrever(vazao_hora["pecas"]),
        "tipo_peca": {t: {"quantidade": int((df["tipo_peca"] == t).sum()),
                          "percentual": round(100 * (df["tipo_peca"] == t).mean(), 2)} for t in ORDEM_TIPOS},
        "status_reservatorio": status,
        "por_turno": por_turno,
        "ciclos_reservatorio": ciclos_esvaziamento(df),
        "correlacoes": {
            "distancia_x_indutivo_metalicas_pearson": round(float(
                df.loc[df["tipo_peca"] == "METALICA", ["distancia_cm", "indutivo_intensidade"]].corr().iloc[0, 1]), 3),
            "distancia_x_capacitivo_nao_metalicas_pearson": round(float(
                df.loc[df["tipo_peca"] == "NAO_METALICA", ["distancia_cm", "capacitivo_intensidade"]].corr().iloc[0, 1]), 3),
        },
        "outliers": {
            "parametros": info_outliers["parametros"],
            "contagem_por_detector": {c: int(sinais[c].sum()) for c in sinais.columns},
            "leituras_com_algum_sinal": int(sinais.any(axis=1).sum()),
            "janelas_10min_vazao": {"baixa": int((vazao_10min["outlier_iqr"] == "BAIXO").sum()),
                                    "alta": int((vazao_10min["outlier_iqr"] == "ALTO").sum()),
                                    "total_janelas": int(len(vazao_10min))},
        },
        "avaliacao_detectores": avaliar_detectores(df, sinais, vazao_10min, eventos),
        "alertas_simulados_regra_backend": simular_alertas(df),
    }
    if "material_real" in df:
        acerto = (((df["tipo_peca"] == "METALICA") & (df["material_real"] == "METAL"))
                  | ((df["tipo_peca"] == "NAO_METALICA") & (df["material_real"] == "NAO_METAL")))
        resultado["classificacao"] = {
            "acuracia_pct": round(100 * acerto.mean(), 2),
            "matriz_confusao": pd.crosstab(df["material_real"], df["tipo_peca"]).to_dict(orient="index"),
        }

    PASTA_RESULTADOS.mkdir(parents=True, exist_ok=True)
    colunas = ["timestamp", "turno", "tipo_peca", "nivel_reservatorio_percentual", "taxa_nivel_pct_min",
               "z_robusto_taxa", "indutivo_intensidade", "capacitivo_intensidade", "detectores"]
    if "evento_simulado" in df_outliers:
        colunas.append("evento_simulado")
    df_outliers[colunas].to_csv(PASTA_RESULTADOS / f"outliers{sufixo}.csv", index=False)
    if gerar_figuras:
        gerar_graficos(df, vazao_10min, sinais, eventos, resultado)
    return resultado


def escrever_resumo(r: dict) -> str:
    nivel = r["nivel_reservatorio"]
    linhas = [
        "RESUMO DA ANÁLISE - ESTEIRA METALTECH",
        f"Período: {r['periodo']['inicio']} a {r['periodo']['fim']} ({r['periodo']['dias']} dias)",
        f"Leituras: {r['total_leituras']}",
        "",
        "Nível do reservatório (%)",
        f"  média {nivel['media']} | mediana {nivel['mediana']} | desvio {nivel['desvio_padrao']} | Q1 {nivel['q1']} | "
        f"Q3 {nivel['q3']} | mín {nivel['minimo']} | máx {nivel['maximo']}",
        "",
        "Tipos de peça: " + ", ".join(f"{k} {v['percentual']}%" for k, v in r["tipo_peca"].items()),
        "Status (% do tempo): " + ", ".join(f"{k} {v['percentual_tempo']}%" for k, v in r["status_reservatorio"].items()),
        "",
        "Por turno: " + " | ".join(f"{t}: {v['pecas_por_hora']['media']} peças/h, crítico {v['percentual_tempo_critico']}% do tempo"
                                    for t, v in r["por_turno"].items()),
        f"Ciclos de esvaziamento: {r['ciclos_reservatorio']}",
        "",
        "Outliers por detector: " + ", ".join(f"{k}={v}" for k, v in r["outliers"]["contagem_por_detector"].items()),
        f"Janelas de vazão (10 min): {r['outliers']['janelas_10min_vazao']}",
        "",
        "Alertas que o backend deve gerar: " + str(r["alertas_simulados_regra_backend"]["alertas_por_tipo"]),
    ]
    if r["avaliacao_detectores"]:
        linhas += ["", "Avaliação contra a verdade-terreno da simulação:"]
        for detector, v in r["avaliacao_detectores"].items():
            linhas.append(f"  {detector}: precisão {v.get('precisao_pct')}% | episódios detectados "
                          f"{v.get('episodios_detectados', '-')}/{v.get('episodios', '-')}")
    return "\n".join(linhas)


def principal() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    analisador = argparse.ArgumentParser(description="Análise estatística da esteira MetalTech")
    analisador.add_argument("--fonte", choices=["csv", "api"], default="csv")
    analisador.add_argument("--csv", type=Path, default=PASTA / "leituras_simuladas.csv")
    analisador.add_argument("--url", default="http://localhost:8080")
    argumentos = analisador.parse_args()

    if argumentos.fonte == "api":
        df, eventos = carregar_api(argumentos.url), None
    else:
        df = carregar_csv(argumentos.csv)
        caminho_eventos = argumentos.csv.with_name("eventos_simulados.csv")
        eventos = pd.read_csv(caminho_eventos) if caminho_eventos.exists() else None

    sufixo = "" if argumentos.fonte == "csv" else "_api"
    resultado = analisar(df, eventos, gerar_figuras=argumentos.fonte == "csv", sufixo=sufixo)
    with open(PASTA_RESULTADOS / f"estatisticas{sufixo}.json", "w", encoding="utf-8") as arquivo:
        json.dump(resultado, arquivo, ensure_ascii=False, indent=2, default=str)
    resumo = escrever_resumo(resultado)
    (PASTA_RESULTADOS / f"resumo{sufixo}.txt").write_text(resumo, encoding="utf-8")
    print(resumo)
    if argumentos.fonte == "csv":
        print(f"\nGráficos salvos em {PASTA_GRAFICOS}")


if __name__ == "__main__":
    principal()
