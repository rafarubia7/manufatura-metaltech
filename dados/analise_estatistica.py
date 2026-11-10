"""
Análise estatística da operação da esteira MetalTech.

Entradas:
    --fonte csv  (padrão) lê dados/leituras_simuladas.csv
    --fonte api  lê as leituras persistidas via GET /api/v1/leituras (paginado)

Saídas:
    dados/resultados/estatisticas.json   indicadores numéricos completos
    dados/resultados/outliers.csv        leituras sinalizadas e o(s) detector(es)
    dados/resultados/resumo.txt          resumo legível para o relatório
    dados/graficos/*.png                 gráficos técnicos

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
TAXA_ENCHIMENTO_ANOMALA = 5.0      # %/min (plano de ação técnico)
TAXA_ESVAZIAMENTO_ANOMALA = -3.0   # %/min (plano de ação técnico)
INTENSIDADE_FALHA = 0.10
JANELA_TAXA_MIN = 3                # janela para a taxa de variação do nível
JANELA_THROUGHPUT = "10min"
Z_ROBUSTO_LIMITE = 3.5

# Paleta (validada para daltonismo nos três primeiros slots) e cores de status
AZUL, LARANJA, VERDE_AGUA = "#2a78d6", "#eb6834", "#1baf7a"
CINZA_SERIE = "#898781"
STATUS_COR = {"NORMAL": "#0ca30c", "ALERTA_BAIXO": "#898781", "ALERTA_ALTO": "#fab219", "CRITICO": "#d03b3b"}
TINTA, TINTA_SEC, GRADE, EIXO = "#0b0b0b", "#52514e", "#e1e0d9", "#c3c2b7"
ORDEM_TIPOS = ["METALICA", "NAO_METALICA", "DESCONHECIDA", "AMBAS"]
ORDEM_STATUS = ["ALERTA_BAIXO", "NORMAL", "ALERTA_ALTO", "CRITICO"]
NOME_TIPO = {"METALICA": "Metálica", "NAO_METALICA": "Não metálica", "DESCONHECIDA": "Desconhecida", "AMBAS": "Ambas (conflito)"}
NOME_STATUS = {"ALERTA_BAIXO": "Alerta baixo (<20%)", "NORMAL": "Normal (20-80%)",
               "ALERTA_ALTO": "Alerta alto (>80%)", "CRITICO": "Crítico (≥95%)"}


# ---------------------------------------------------------------------------
# Carga
# ---------------------------------------------------------------------------

def carregar_csv(caminho: Path) -> pd.DataFrame:
    return pd.read_csv(caminho)


def carregar_api(url: str) -> pd.DataFrame:
    import requests

    linhas, pagina = [], 0
    while True:
        r = requests.get(f"{url}/api/v1/leituras", params={"page": pagina, "size": 1000, "sort": "timestamp,asc"}, timeout=60)
        r.raise_for_status()
        corpo = r.json()
        for l in corpo["content"]:
            linhas.append({
                "timestamp": l["timestamp"],
                "distancia_cm": l["sensorDistancia"]["valor"],
                "indutivo_detectou_metal": l["sensorIndutivo"]["detectouMetal"],
                "indutivo_intensidade": l["sensorIndutivo"]["intensidade"],
                "capacitivo_detectou_nao_metal": l["sensorCapacitivo"]["detectouNaoMetal"],
                "capacitivo_intensidade": l["sensorCapacitivo"]["intensidade"],
                "tipo_peca": l["peca"]["tipo"],
                "nivel_reservatorio_percentual": l["nivelReservatorio"]["percentual"],
                "status_reservatorio": l["nivelReservatorio"]["status"],
                "dispositivo_id": l["dispositivoId"],
            })
        if corpo["last"]:
            break
        pagina += 1
    return pd.DataFrame(linhas)


def preparar(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["timestamp"] = pd.to_datetime(df["timestamp"], utc=True, format="ISO8601").dt.tz_convert(FUSO)
    df = df.sort_values("timestamp", kind="stable").reset_index(drop=True)
    hora = df["timestamp"].dt.hour
    if "turno" not in df:
        df["turno"] = np.select([(hora >= 6) & (hora < 14), (hora >= 14) & (hora < 22)], ["T1", "T2"], "T3")
    # Duração representada por cada leitura (até a próxima) para medir tempo em cada status
    df["duracao_min"] = df["timestamp"].diff().shift(-1).dt.total_seconds().div(60).fillna(0)
    df["taxa_nivel_pct_min"] = taxa_variacao(df)
    return df


def taxa_variacao(df: pd.DataFrame) -> pd.Series:
    """Variação do nível em %/min numa janela móvel de JANELA_TAXA_MIN minutos.

    Compara cada leitura com a primeira leitura DENTRO da janela, para que um período sem
    leituras (esteira parada) não dilua a taxa da rajada que vem logo depois.
    Janelas com menos de 1 minuto de dados ficam sem taxa (ruído de peça a peça)."""
    base = df[["timestamp", "nivel_reservatorio_percentual"]].rename(
        columns={"timestamp": "t_ant", "nivel_reservatorio_percentual": "nivel_ant"})
    alvo = pd.DataFrame({"t_ref": df["timestamp"] - pd.Timedelta(minutes=JANELA_TAXA_MIN)})
    anterior = pd.merge_asof(alvo, base, left_on="t_ref", right_on="t_ant", direction="forward")
    dt = (df["timestamp"] - anterior["t_ant"]).dt.total_seconds() / 60
    taxa = (df["nivel_reservatorio_percentual"] - anterior["nivel_ant"]) / dt
    return taxa.where(dt >= 1).round(3)


# ---------------------------------------------------------------------------
# Estatística descritiva e indicadores operacionais
# ---------------------------------------------------------------------------

def descrever(serie: pd.Series) -> dict:
    s = serie.dropna()
    q1, q2, q3 = s.quantile([0.25, 0.5, 0.75])
    return {
        "n": int(s.size), "media": round(s.mean(), 3), "mediana": round(q2, 3), "desvio_padrao": round(s.std(), 3),
        "minimo": round(s.min(), 3), "q1": round(q1, 3), "q3": round(q3, 3), "iqr": round(q3 - q1, 3),
        "maximo": round(s.max(), 3), "assimetria": round(float(stats.skew(s)), 3),
        "curtose": round(float(stats.kurtosis(s)), 3), "cv_pct": round(100 * s.std() / s.mean(), 2) if s.mean() else None,
    }


def throughput(df: pd.DataFrame, freq: str) -> pd.DataFrame:
    serie = df.set_index("timestamp").resample(freq).size().rename("pecas").to_frame()
    hora = serie.index.hour
    serie["turno"] = np.select([(hora >= 6) & (hora < 14), (hora >= 14) & (hora < 22)], ["T1", "T2"], "T3")
    return serie


def ciclos_esvaziamento(df: pd.DataFrame, queda_minima: float = 25.0, retomada: float = 5.0) -> dict:
    """Identifica ciclos pico -> vale com histerese, robusto ao ruído de peça a peça.

    Um esvaziamento começa no pico do nível e termina no vale seguinte; só conta se o nível
    cair pelo menos `queda_minima` pontos e voltar a subir `retomada` pontos depois do vale."""
    t, n = df["timestamp"].to_numpy(), df["nivel_reservatorio_percentual"].to_numpy()
    ciclos, enchendo = [], True
    i_pico = i_vale = 0
    for i in range(len(n)):
        if enchendo:
            if n[i] >= n[i_pico]:
                i_pico = i
            elif n[i_pico] - n[i] >= queda_minima:
                enchendo, i_vale = False, i
        else:
            if n[i] <= n[i_vale]:
                i_vale = i
            elif n[i] - n[i_vale] >= retomada:
                ciclos.append((i_pico, i_vale))
                enchendo, i_pico = True, i
    minutos = lambda a, b: (pd.Timestamp(t[b]) - pd.Timestamp(t[a])).total_seconds() / 60
    esvaziamentos = [minutos(p, v) for p, v in ciclos]
    enchimentos = [minutos(v, p2) for (_, v), (p2, _) in zip(ciclos, ciclos[1:])]
    picos = [float(n[p]) for p, _ in ciclos]
    vales = [float(n[v]) for _, v in ciclos]
    dias = (df["timestamp"].iloc[-1] - df["timestamp"].iloc[0]).total_seconds() / 86400
    return {
        "quantidade": len(ciclos),
        "por_dia": round(len(ciclos) / max(dias, 1), 2),
        "duracao_media_esvaziamento_min": round(float(np.mean(esvaziamentos)), 1) if esvaziamentos else None,
        "tempo_medio_enchimento_min": round(float(np.mean(enchimentos)), 1) if enchimentos else None,
        "tempo_mediano_enchimento_min": round(float(np.median(enchimentos)), 1) if enchimentos else None,
        "nivel_medio_no_inicio_do_esvaziamento": round(float(np.mean(picos)), 2) if picos else None,
        "nivel_medio_ao_fim_do_esvaziamento": round(float(np.mean(vales)), 2) if vales else None,
        "esvaziamentos_iniciados_acima_de_95pct": int(sum(p >= LIMITE_CRITICO for p in picos)),
        "esvaziamentos_iniciados_abaixo_de_80pct": int(sum(p <= LIMITE_ALTO for p in picos)),
    }


def simular_alertas(df: pd.DataFrame) -> dict:
    """Reproduz a regra do backend: um alerta de reservatório ativo por tipo, resolvido quando a condição muda;
    conflito de sensores (AMBAS) gera alerta sempre."""
    mapa = {"ALERTA_BAIXO": "RESERVATORIO_BAIXO", "ALERTA_ALTO": "RESERVATORIO_ALTO", "CRITICO": "RESERVATORIO_CRITICO"}
    contagem = {"RESERVATORIO_BAIXO": 0, "RESERVATORIO_ALTO": 0, "RESERVATORIO_CRITICO": 0, "CONTAGEM_ANOMALA": 0}
    ativo, aberto_em, duracoes = None, None, {k: [] for k in mapa.values()}
    for t, status, tipo in zip(df["timestamp"], df["status_reservatorio"], df["tipo_peca"]):
        atual = mapa.get(status)
        if ativo and ativo != atual:
            duracoes[ativo].append((t - aberto_em).total_seconds() / 60)
            ativo = None
        if atual and ativo is None:
            ativo, aberto_em = atual, t
            contagem[atual] += 1
        if tipo == "AMBAS":
            contagem["CONTAGEM_ANOMALA"] += 1
    return {
        "alertas_por_tipo": contagem,
        "alertas_ativos_ao_final": (1 if ativo else 0),
        "tipo_ativo_ao_final": ativo,
        "duracao_media_alerta_min": {k: (round(float(np.mean(v)), 1) if v else None) for k, v in duracoes.items()},
        "alertas_por_dia": round(sum(contagem.values()) / max((df["timestamp"].iloc[-1] - df["timestamp"].iloc[0]).total_seconds() / 86400, 1), 1),
    }


# ---------------------------------------------------------------------------
# Outliers
# ---------------------------------------------------------------------------

def detectar_outliers(df: pd.DataFrame, tp10: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, dict]:
    det = pd.DataFrame(index=df.index)
    det["R1_enchimento_acima_5pct_min"] = df["taxa_nivel_pct_min"] > TAXA_ENCHIMENTO_ANOMALA
    det["R2_esvaziamento_acima_3pct_min"] = df["taxa_nivel_pct_min"] < TAXA_ESVAZIAMENTO_ANOMALA
    # Peças não metálicas sempre têm indutivo baixo; falha de conexão = os DOIS sensores sem sinal
    det["R3_sinal_nulo_nos_sensores"] = (df["indutivo_intensidade"] < INTENSIDADE_FALHA) & (df["capacitivo_intensidade"] < INTENSIDADE_FALHA)
    det["R4_conflito_ambas"] = df["tipo_peca"] == "AMBAS"

    taxa = df["taxa_nivel_pct_min"]
    mediana = taxa.median()
    mad = (taxa - mediana).abs().median() * 1.4826
    df["z_robusto_taxa"] = ((taxa - mediana) / mad).round(2)
    det["Z_taxa_nivel_robusto"] = df["z_robusto_taxa"].abs() > Z_ROBUSTO_LIMITE

    # IQR do throughput (10 min) por turno: janelas anormalmente vazias (gargalo) ou cheias (rajada)
    limites = {}
    tp10 = tp10.copy()
    tp10["outlier_iqr"] = ""
    for turno, g in tp10.groupby("turno"):
        q1, q3 = g["pecas"].quantile([0.25, 0.75])
        inf, sup = q1 - 1.5 * (q3 - q1), q3 + 1.5 * (q3 - q1)
        limites[turno] = {"q1": float(q1), "q3": float(q3), "limite_inferior": float(inf), "limite_superior": float(sup)}
        tp10.loc[g.index[g["pecas"] < max(inf, 0.5)], "outlier_iqr"] = "BAIXO"
        tp10.loc[g.index[g["pecas"] > sup], "outlier_iqr"] = "ALTO"
    janela = df["timestamp"].dt.floor(JANELA_THROUGHPUT)
    det["IQR_throughput_alto"] = janela.map(tp10["outlier_iqr"]).eq("ALTO").values

    df_out = df[det.any(axis=1)].copy()
    df_out["detectores"] = det[det.any(axis=1)].apply(lambda r: ";".join(r.index[r]), axis=1)
    return det, tp10, {"mediana_taxa": round(float(mediana), 4), "mad_escalado": round(float(mad), 4),
                       "limites_iqr_throughput_10min": limites, "df_out": df_out}


def avaliar_detectores(df: pd.DataFrame, det: pd.DataFrame, tp10: pd.DataFrame, eventos: pd.DataFrame | None) -> dict:
    """Compara os detectores com a verdade-terreno da simulação (disponível apenas na fonte CSV)."""
    if "evento_simulado" not in df:
        return {}
    alvo = {
        "R1_enchimento_acima_5pct_min": "RAJADA_POS_GARGALO",
        "R2_esvaziamento_acima_3pct_min": "ESVAZIAMENTO_BRUSCO",
        "R3_sinal_nulo_nos_sensores": "FALHA_CONEXAO",
        "R4_conflito_ambas": "DERIVA_CAPACITIVO",
        "IQR_throughput_alto": "RAJADA_POS_GARGALO",
    }
    episodio = (df["evento_simulado"] != df["evento_simulado"].shift()).cumsum()
    resultado = {}
    for detector, evento in alvo.items():
        sinal, verdade = det[detector], df["evento_simulado"] == evento
        vp = int((sinal & verdade).sum())
        episodios = episodio[verdade].unique()
        detectados = episodio[verdade & sinal].unique()
        resultado[detector] = {
            "evento_alvo": evento, "leituras_sinalizadas": int(sinal.sum()),
            "precisao_pct": round(100 * vp / sinal.sum(), 1) if sinal.sum() else None,
            "episodios": int(len(episodios)), "episodios_detectados": int(len(detectados)),
            "recall_episodios_pct": round(100 * len(detectados) / len(episodios), 1) if len(episodios) else None,
        }
    # Z robusto: avalia contra qualquer anomalia que mexa no nível
    sinal = det["Z_taxa_nivel_robusto"]
    verdade = df["evento_simulado"].isin(["RAJADA_POS_GARGALO", "ESVAZIAMENTO_BRUSCO"])
    resultado["Z_taxa_nivel_robusto"] = {
        "evento_alvo": "RAJADA_POS_GARGALO ou ESVAZIAMENTO_BRUSCO", "leituras_sinalizadas": int(sinal.sum()),
        "precisao_pct": round(100 * (sinal & verdade).sum() / sinal.sum(), 1) if sinal.sum() else None,
        "observacao": "sinaliza também esvaziamentos controlados (variação legítima, porém rara)",
    }
    # Gargalos: não geram leituras, então são avaliados pelas janelas de throughput baixo
    if eventos is not None:
        gargalos = eventos[eventos["tipo"] == "GARGALO"]
        baixos = tp10.index[tp10["outlier_iqr"] == "BAIXO"]
        detectados = 0
        for _, g in gargalos.iterrows():
            ini, fim = pd.Timestamp(g["inicio"]).tz_convert(FUSO), pd.Timestamp(g["fim"]).tz_convert(FUSO)
            if any((b + pd.Timedelta(JANELA_THROUGHPUT) > ini) and (b < fim) for b in baixos):
                detectados += 1
        janelas_em_gargalo = sum(
            any((b + pd.Timedelta(JANELA_THROUGHPUT) > pd.Timestamp(g["inicio"]).tz_convert(FUSO)) and
                (b < pd.Timestamp(g["fim"]).tz_convert(FUSO)) for _, g in gargalos.iterrows()) for b in baixos)
        resultado["IQR_throughput_baixo"] = {
            "evento_alvo": "GARGALO", "janelas_sinalizadas": int(len(baixos)),
            "precisao_pct": round(100 * janelas_em_gargalo / len(baixos), 1) if len(baixos) else None,
            "episodios": int(len(gargalos)), "episodios_detectados": detectados,
            "recall_episodios_pct": round(100 * detectados / len(gargalos), 1) if len(gargalos) else None,
        }
    return resultado


# ---------------------------------------------------------------------------
# Gráficos
# ---------------------------------------------------------------------------

def estilo():
    plt.rcParams.update({
        "figure.dpi": 150, "savefig.dpi": 150, "font.family": ["Segoe UI", "DejaVu Sans", "sans-serif"],
        "font.size": 9.5, "axes.titlesize": 11.5, "axes.titleweight": "bold", "axes.titlelocation": "left",
        "axes.edgecolor": EIXO, "axes.labelcolor": TINTA_SEC, "axes.grid": True, "grid.color": GRADE,
        "grid.linewidth": 0.6, "axes.spines.top": False, "axes.spines.right": False,
        "xtick.color": TINTA_SEC, "ytick.color": TINTA_SEC, "text.color": TINTA, "legend.frameon": False,
        "figure.facecolor": "#fcfcfb", "axes.facecolor": "#fcfcfb", "savefig.facecolor": "#fcfcfb",
        "axes.axisbelow": True,
    })


def linhas_limite(ax, horizontal=True, rotulos=True):
    for valor, cor, nome in [(LIMITE_BAIXO, STATUS_COR["ALERTA_BAIXO"], "Baixo 20%"),
                             (LIMITE_ALTO, STATUS_COR["ALERTA_ALTO"], "Alto 80%"),
                             (LIMITE_CRITICO, STATUS_COR["CRITICO"], "Crítico 95%")]:
        if horizontal:
            ax.axhline(valor, color=cor, lw=1, ls="--", zorder=1)
            if rotulos:
                ax.annotate(nome, xy=(1.0, valor), xycoords=("axes fraction", "data"), xytext=(4, 0),
                            textcoords="offset points", va="center", fontsize=8, color=TINTA_SEC)
        else:
            ax.axvline(valor, color=cor, lw=1, ls="--", zorder=1)
            if rotulos:
                ax.annotate(nome, xy=(valor, 1.0), xycoords=("data", "axes fraction"), xytext=(0, 3),
                            textcoords="offset points", ha="center", fontsize=8, color=TINTA_SEC)


def salvar(fig, nome):
    PASTA_GRAFICOS.mkdir(parents=True, exist_ok=True)
    fig.savefig(PASTA_GRAFICOS / nome, bbox_inches="tight")
    plt.close(fig)


def eixo_tempo(ax, formato="%d/%m\n%Hh"):
    ax.xaxis.set_major_formatter(mdates.DateFormatter(formato, tz=FUSO))


def graficos(df, tp_hora, tp10, det, eventos, resultado):
    estilo()
    nivel = df["nivel_reservatorio_percentual"]

    # 1. Série temporal do nível - semana completa
    fig, ax = plt.subplots(figsize=(11, 4))
    if eventos is not None:
        for _, e in eventos[eventos["tipo"] == "GARGALO"].iterrows():
            ax.axvspan(pd.Timestamp(e["inicio"]), pd.Timestamp(e["fim"]), color=LARANJA, alpha=0.18, lw=0)
    ax.plot(df["timestamp"], nivel, color=AZUL, lw=0.8)
    linhas_limite(ax)
    ax.set_ylim(0, 102)
    ax.set_ylabel("Nível do reservatório (%)")
    ax.set_title("Nível do reservatório ao longo da semana")
    if eventos is not None:
        ax.text(0, -0.2, "Faixas laranja: esteira parada por atolamento (gargalo)", transform=ax.transAxes,
                fontsize=8, color=TINTA_SEC)
    eixo_tempo(ax)
    salvar(fig, "01_serie_temporal_nivel_semana.png")

    # 2. Zoom de 24h no dia com mais eventos anômalos
    dia = df.loc[df.get("evento_simulado", pd.Series("NORMAL", index=df.index)) != "NORMAL", "timestamp"].dt.date
    dia_alvo = dia.mode().iloc[0] if not dia.empty else df["timestamp"].dt.date.iloc[0]
    d = df[df["timestamp"].dt.date == dia_alvo]
    fig, ax = plt.subplots(figsize=(11, 4))
    ax.plot(d["timestamp"], d["nivel_reservatorio_percentual"], color=AZUL, lw=1.2, label="Nível")
    if "comporta_aberta" in d:
        ax.fill_between(d["timestamp"], 0, 102, where=d["comporta_aberta"], color=VERDE_AGUA, alpha=0.15, lw=0,
                        label="Comporta de descarga aberta")
    if eventos is not None:
        for _, e in eventos[eventos["tipo"] == "GARGALO"].iterrows():
            ini = pd.Timestamp(e["inicio"])
            if ini.date() == dia_alvo:
                ax.axvspan(ini, pd.Timestamp(e["fim"]), color=LARANJA, alpha=0.2, lw=0)
                ax.annotate("gargalo", xy=(ini, 99), fontsize=8, color=TINTA_SEC)
    criticos = d[d["status_reservatorio"] == "CRITICO"]
    ax.scatter(criticos["timestamp"], criticos["nivel_reservatorio_percentual"], s=10, color=STATUS_COR["CRITICO"],
               zorder=3, label="Leitura em nível crítico")
    linhas_limite(ax)
    ax.set_ylim(0, 102)
    ax.set_ylabel("Nível do reservatório (%)")
    ax.set_title(f"Ciclos de enchimento e esvaziamento em 24 h ({dia_alvo:%d/%m/%Y})")
    ax.legend(loc="upper left", bbox_to_anchor=(0, -0.12), ncol=3)
    eixo_tempo(ax, "%Hh")
    salvar(fig, "02_serie_temporal_nivel_24h.png")

    # 3. Throughput por hora e tipo de peça
    contagem = df.set_index("timestamp").groupby([pd.Grouper(freq="1h"), "tipo_peca"]).size().unstack(fill_value=0)
    contagem["OUTRAS"] = contagem.get("DESCONHECIDA", 0) + contagem.get("AMBAS", 0)
    fig, ax = plt.subplots(figsize=(11, 4))
    for col, cor, nome in [("METALICA", AZUL, "Metálica"), ("NAO_METALICA", LARANJA, "Não metálica"),
                           ("OUTRAS", VERDE_AGUA, "Desconhecida + conflito")]:
        ax.plot(contagem.index, contagem[col], color=cor, lw=1.2, label=nome)
        ax.annotate(nome, xy=(contagem.index[-1], contagem[col].iloc[-1]), xytext=(6, 0),
                    textcoords="offset points", va="center", fontsize=8, color=TINTA_SEC)
    ax.set_ylabel("Peças por hora")
    ax.set_title("Throughput horário por tipo de peça (ciclo diário de turnos)")
    ax.legend(loc="upper left", ncol=3)
    eixo_tempo(ax)
    salvar(fig, "03_serie_temporal_throughput_tipo.png")

    # 4. Histograma do nível
    fig, ax = plt.subplots(figsize=(8, 4))
    ax.hist(nivel, bins=np.arange(0, 102, 2), color=AZUL, edgecolor="#fcfcfb", linewidth=1)
    linhas_limite(ax, horizontal=False)
    r = resultado["nivel_reservatorio"]
    ax.axvline(r["media"], color=TINTA, lw=1)
    ax.annotate(f"média {r['media']:.1f}%", xy=(r["media"], 0.92), xycoords=("data", "axes fraction"),
                xytext=(4, 0), textcoords="offset points", fontsize=8)
    ax.set_xlabel("Nível do reservatório (%)")
    ax.set_ylabel("Leituras")
    ax.set_title("Distribuição do nível do reservatório", pad=14)
    salvar(fig, "04_histograma_nivel.png")

    # 5. Boxplots por turno (nível e throughput)
    for nome, dados, ylabel, titulo in [
        ("05_boxplot_nivel_por_turno.png", [df.loc[df["turno"] == t, "nivel_reservatorio_percentual"] for t in ["T1", "T2", "T3"]],
         "Nível do reservatório (%)", "Nível do reservatório por turno"),
        ("06_boxplot_throughput_por_turno.png", [tp10.loc[tp10["turno"] == t, "pecas"] for t in ["T1", "T2", "T3"]],
         "Peças a cada 10 min", "Throughput em janelas de 10 min por turno (pontos = outliers IQR)"),
    ]:
        fig, ax = plt.subplots(figsize=(7, 4))
        ax.boxplot(dados, tick_labels=["T1 (06-14h)", "T2 (14-22h)", "T3 (22-06h)"], widths=0.45, patch_artist=True,
                   boxprops=dict(facecolor="#cde2fb", edgecolor=AZUL), medianprops=dict(color=TINTA, lw=1.5),
                   whiskerprops=dict(color=AZUL), capprops=dict(color=AZUL),
                   flierprops=dict(marker="o", markersize=4, markerfacecolor=LARANJA, markeredgecolor="#fcfcfb"))
        if "nivel" in nome:
            linhas_limite(ax)
        ax.set_ylabel(ylabel)
        ax.set_title(titulo)
        salvar(fig, nome)

    # 6. Barras: proporção por tipo de peça
    prop = df["tipo_peca"].value_counts(normalize=True).reindex(ORDEM_TIPOS, fill_value=0) * 100
    fig, ax = plt.subplots(figsize=(8, 3.2))
    barras = ax.barh([NOME_TIPO[t] for t in prop.index][::-1], prop.values[::-1], color=AZUL, height=0.55)
    for b, v in zip(barras, prop.values[::-1]):
        ax.annotate(f"{v:.1f}%", xy=(b.get_width(), b.get_y() + b.get_height() / 2), xytext=(4, 0),
                    textcoords="offset points", va="center", fontsize=9)
    ax.set_xlabel("% das leituras")
    ax.grid(axis="y", visible=False)
    ax.set_title("Proporção de peças por tipo classificado")
    salvar(fig, "07_barras_tipo_peca.png")

    # 7. Barras: distribuição do status (contagem e tempo)
    st = resultado["status_reservatorio"]
    fig, ax = plt.subplots(figsize=(8, 3.2))
    rotulos = [NOME_STATUS[s] for s in ORDEM_STATUS]
    valores = [st[s]["percentual_tempo"] for s in ORDEM_STATUS]
    barras = ax.barh(rotulos[::-1], valores[::-1], color=[STATUS_COR[s] for s in ORDEM_STATUS][::-1], height=0.55)
    for b, v in zip(barras, valores[::-1]):
        ax.annotate(f"{v:.1f}%", xy=(b.get_width(), b.get_y() + b.get_height() / 2), xytext=(4, 0),
                    textcoords="offset points", va="center", fontsize=9)
    ax.set_xlabel("% do tempo de operação")
    ax.grid(axis="y", visible=False)
    ax.set_title("Tempo do reservatório em cada faixa de status")
    salvar(fig, "08_barras_status_reservatorio.png")

    # 8. Dispersão distância x intensidade (um painel por sensor)
    fig, eixos = plt.subplots(1, 2, figsize=(11, 4), sharey=True)
    metal = df["material_real"] == "METAL" if "material_real" in df else df["tipo_peca"].isin(["METALICA", "AMBAS"])
    paineis = [(eixos[0], df[metal], "indutivo_intensidade", 0.50, "Sensor indutivo - peças metálicas"),
               (eixos[1], df[~metal], "capacitivo_intensidade", 0.45, "Sensor capacitivo - peças não metálicas")]
    for ax, d, col, limiar, titulo in paineis:
        normal = d[d[col] >= INTENSIDADE_FALHA]
        falha = d[d[col] < INTENSIDADE_FALHA]
        ax.scatter(normal["distancia_cm"], normal[col], s=8, alpha=0.35, color=AZUL, edgecolors="none",
                   label="Leituras")
        ax.scatter(falha["distancia_cm"], falha[col], s=12, color=LARANJA, edgecolors="none",
                   label="Sinal < 0,10 (possível falha)")
        ax.axhline(limiar, color=TINTA_SEC, lw=1, ls="--")
        ax.annotate(f"limiar de detecção {limiar:.2f}", xy=(25, limiar), xytext=(0, 4), textcoords="offset points",
                    ha="right", fontsize=8, color=TINTA_SEC)
        ax.set_xlabel("Distância peça-sensor (cm)")
        ax.set_title(titulo, fontsize=10.5)
        ax.legend(loc="upper right")
    eixos[0].set_ylabel("Intensidade normalizada")
    fig.suptitle("Distância x intensidade: o sinal decai com a distância e define o alcance útil",
                 x=0.07, ha="left", fontweight="bold", fontsize=11.5)
    salvar(fig, "09_dispersao_distancia_intensidade.png")

    # 9. Taxa de variação do nível com outliers
    fig, ax = plt.subplots(figsize=(11, 4))
    ax.plot(df["timestamp"], df["taxa_nivel_pct_min"], color=CINZA_SERIE, lw=0.5, label="Taxa de variação (janela 3 min)")
    r1 = df[det["R1_enchimento_acima_5pct_min"]]
    r2 = df[det["R2_esvaziamento_acima_3pct_min"]]
    ax.scatter(r1["timestamp"], r1["taxa_nivel_pct_min"], s=14, color=LARANJA, zorder=3, label="Enchimento > 5%/min")
    ax.scatter(r2["timestamp"], r2["taxa_nivel_pct_min"], s=14, color=AZUL, zorder=3, label="Esvaziamento > 3%/min")
    ax.axhline(TAXA_ENCHIMENTO_ANOMALA, color=LARANJA, lw=1, ls="--")
    ax.axhline(TAXA_ESVAZIAMENTO_ANOMALA, color=AZUL, lw=1, ls="--")
    ax.set_ylabel("Variação do nível (%/min)")
    ax.set_title("Taxa de variação do nível e outliers operacionais")
    ax.legend(loc="upper left", bbox_to_anchor=(0, -0.12), ncol=3)
    eixo_tempo(ax)
    salvar(fig, "10_taxa_variacao_outliers.png")


# ---------------------------------------------------------------------------
# Execução
# ---------------------------------------------------------------------------

def analisar(df: pd.DataFrame, eventos: pd.DataFrame | None, gerar_graficos: bool = True, sufixo: str = "") -> dict:
    df = preparar(df)
    tp_hora = throughput(df, "1h")
    tp10 = throughput(df, JANELA_THROUGHPUT)
    total = len(df)

    tempo_total = df["duracao_min"].sum()
    status = {}
    for s in ORDEM_STATUS:
        m = df["status_reservatorio"] == s
        status[s] = {"leituras": int(m.sum()), "percentual_leituras": round(100 * m.mean(), 2),
                     "percentual_tempo": round(100 * df.loc[m, "duracao_min"].sum() / tempo_total, 2)}

    por_turno = {}
    for t, g in df.groupby("turno"):
        tph = tp_hora[tp_hora["turno"] == t]["pecas"]
        por_turno[t] = {
            "leituras": int(len(g)),
            "pecas_por_hora": descrever(tph),
            "percentual_por_tipo": (g["tipo_peca"].value_counts(normalize=True) * 100).round(2).reindex(ORDEM_TIPOS, fill_value=0).to_dict(),
            "percentual_tempo_critico": round(100 * g.loc[g["status_reservatorio"] == "CRITICO", "duracao_min"].sum() / g["duracao_min"].sum(), 2),
            "nivel_medio": round(g["nivel_reservatorio_percentual"].mean(), 2),
        }

    det, tp10, info_out = detectar_outliers(df, tp10)
    df_out = info_out.pop("df_out")
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
            "status_inconsistente_com_regra": int((df["status_reservatorio"] != df["nivel_reservatorio_percentual"].map(
                lambda p: "CRITICO" if p >= 95 else "ALERTA_ALTO" if p > 80 else "ALERTA_BAIXO" if p < 20 else "NORMAL")).sum()),
            "intervalo_mediano_entre_leituras_s": round(float(df["timestamp"].diff().dt.total_seconds().median()), 1),
            "maior_intervalo_sem_leitura_min": round(float(df["timestamp"].diff().dt.total_seconds().max() / 60), 1),
        },
        "nivel_reservatorio": descrever(df["nivel_reservatorio_percentual"]),
        "distancia_cm": descrever(df["distancia_cm"]),
        "indutivo_intensidade": descrever(df["indutivo_intensidade"]),
        "capacitivo_intensidade": descrever(df["capacitivo_intensidade"]),
        "taxa_variacao_nivel_pct_min": descrever(df["taxa_nivel_pct_min"]),
        "pecas_por_hora": descrever(tp_hora["pecas"]),
        "tipo_peca": {t: {"quantidade": int((df["tipo_peca"] == t).sum()),
                          "percentual": round(100 * (df["tipo_peca"] == t).mean(), 2)} for t in ORDEM_TIPOS},
        "status_reservatorio": status,
        "por_turno": por_turno,
        "ciclos_reservatorio": ciclos_esvaziamento(df),
        "correlacoes": {
            "distancia_x_indutivo_metalicas_pearson": round(float(df.loc[df["tipo_peca"] == "METALICA", ["distancia_cm", "indutivo_intensidade"]].corr().iloc[0, 1]), 3),
            "distancia_x_capacitivo_nao_metalicas_pearson": round(float(df.loc[df["tipo_peca"] == "NAO_METALICA", ["distancia_cm", "capacitivo_intensidade"]].corr().iloc[0, 1]), 3),
        },
        "outliers": {
            "parametros": info_out,
            "contagem_por_detector": {c: int(det[c].sum()) for c in det.columns},
            "leituras_com_algum_sinal": int(det.any(axis=1).sum()),
            "janelas_10min_throughput": {"baixo": int((tp10["outlier_iqr"] == "BAIXO").sum()),
                                         "alto": int((tp10["outlier_iqr"] == "ALTO").sum()),
                                         "total_janelas": int(len(tp10))},
        },
        "avaliacao_detectores": avaliar_detectores(df, det, tp10, eventos),
        "alertas_simulados_regra_backend": simular_alertas(df),
    }
    if "material_real" in df:
        acerto = ((df["tipo_peca"] == "METALICA") & (df["material_real"] == "METAL")) | \
                 ((df["tipo_peca"] == "NAO_METALICA") & (df["material_real"] == "NAO_METAL"))
        resultado["classificacao"] = {
            "acuracia_pct": round(100 * acerto.mean(), 2),
            "matriz_confusao": pd.crosstab(df["material_real"], df["tipo_peca"]).to_dict(orient="index"),
        }

    PASTA_RESULTADOS.mkdir(parents=True, exist_ok=True)
    colunas = ["timestamp", "turno", "tipo_peca", "nivel_reservatorio_percentual", "taxa_nivel_pct_min",
               "z_robusto_taxa", "indutivo_intensidade", "capacitivo_intensidade", "detectores"]
    if "evento_simulado" in df_out:
        colunas.append("evento_simulado")
    df_out[colunas].to_csv(PASTA_RESULTADOS / f"outliers{sufixo}.csv", index=False)
    if gerar_graficos:
        graficos(df, tp_hora, tp10, det, eventos, resultado)
    return resultado


def escrever_resumo(r: dict) -> str:
    n = r["nivel_reservatorio"]
    linhas = [
        "RESUMO DA ANÁLISE - ESTEIRA METALTECH",
        f"Período: {r['periodo']['inicio']} a {r['periodo']['fim']} ({r['periodo']['dias']} dias)",
        f"Leituras: {r['total_leituras']}",
        "",
        "Nível do reservatório (%)",
        f"  média {n['media']} | mediana {n['mediana']} | desvio {n['desvio_padrao']} | Q1 {n['q1']} | Q3 {n['q3']} | "
        f"mín {n['minimo']} | máx {n['maximo']}",
        "",
        "Tipos de peça: " + ", ".join(f"{k} {v['percentual']}%" for k, v in r["tipo_peca"].items()),
        "Status (% do tempo): " + ", ".join(f"{k} {v['percentual_tempo']}%" for k, v in r["status_reservatorio"].items()),
        "",
        "Por turno: " + " | ".join(f"{t}: {v['pecas_por_hora']['media']} peças/h, crítico {v['percentual_tempo_critico']}% do tempo"
                                    for t, v in r["por_turno"].items()),
        f"Ciclos de esvaziamento: {r['ciclos_reservatorio']}",
        "",
        "Outliers por detector: " + ", ".join(f"{k}={v}" for k, v in r["outliers"]["contagem_por_detector"].items()),
        f"Janelas de throughput (10 min): {r['outliers']['janelas_10min_throughput']}",
        "",
        "Alertas que o backend deve gerar: " + str(r["alertas_simulados_regra_backend"]["alertas_por_tipo"]),
    ]
    if r["avaliacao_detectores"]:
        linhas += ["", "Avaliação contra a verdade-terreno da simulação:"]
        for k, v in r["avaliacao_detectores"].items():
            linhas.append(f"  {k}: precisão {v.get('precisao_pct')}% | episódios detectados "
                          f"{v.get('episodios_detectados', '-')}/{v.get('episodios', '-')}")
    return "\n".join(linhas)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Análise estatística da esteira MetalTech")
    parser.add_argument("--fonte", choices=["csv", "api"], default="csv")
    parser.add_argument("--csv", type=Path, default=PASTA / "leituras_simuladas.csv")
    parser.add_argument("--url", default="http://localhost:8080")
    args = parser.parse_args()

    if args.fonte == "api":
        df, eventos = carregar_api(args.url), None
    else:
        df = carregar_csv(args.csv)
        caminho_eventos = args.csv.with_name("eventos_simulados.csv")
        eventos = pd.read_csv(caminho_eventos) if caminho_eventos.exists() else None

    sufixo = "" if args.fonte == "csv" else "_api"
    resultado = analisar(df, eventos, gerar_graficos=args.fonte == "csv", sufixo=sufixo)
    with open(PASTA_RESULTADOS / f"estatisticas{sufixo}.json", "w", encoding="utf-8") as f:
        json.dump(resultado, f, ensure_ascii=False, indent=2, default=str)
    resumo = escrever_resumo(resultado)
    (PASTA_RESULTADOS / f"resumo{sufixo}.txt").write_text(resumo, encoding="utf-8")
    print(resumo)
    if args.fonte == "csv":
        print(f"\nGráficos salvos em {PASTA_GRAFICOS}")


if __name__ == "__main__":
    main()
