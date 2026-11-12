"""
Gera o dataset sintético da esteira de triagem MetalTech.

Cada linha é uma peça passando pelos sensores, com o nível do reservatório no mesmo instante.
A simulação é orientada a eventos: turnos, lotes, decaimento do sinal com a distância,
enchimento gradual, esvaziamento por comporta e anomalias rotuladas em `evento_simulado`.

Uso:
    python gerar_dataset.py
    python gerar_dataset.py --dias 3 --seed 7 --saida outro.csv
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import pandas as pd

PASTA = Path(__file__).resolve().parent
FUSO = timezone(timedelta(hours=-3))

# (nome, hora inicial, peças/min, atraso médio de reação do operador em min)
TURNOS = [
    ("T1", 6, 1.30, 15.0),
    ("T2", 14, 1.10, 20.0),
    ("T3", 22, 0.60, 45.0),
]

LIMITE_BAIXO, LIMITE_ALTO, LIMITE_CRITICO = 20.0, 80.0, 95.0

VOLUME_METALICA = (0.40, 0.05)
VOLUME_NAO_METALICA = (0.46, 0.06)
VAZAO_COMPORTA = (2.0, 0.3)
NIVEL_FIM_ESVAZIAMENTO = 12.0
NIVEL_MIN_ESVAZIAMENTO_TURNO = 50.0

DISTANCIA_MEDIA, DISTANCIA_DESVIO = 8.0, 2.0
PROB_DESALINHADA = 0.04
LIMIAR_DETECCAO_INDUTIVO = 0.50
LIMIAR_DETECCAO_CAPACITIVO = 0.45

GARGALOS_POR_DIA = 2.0
FALHAS_CONEXAO_POR_DIA = 1.0
ESVAZIAMENTOS_BRUSCOS_POR_DIA = 0.5
DERIVAS_CAPACITIVO_NA_SEMANA = 2


@dataclass
class Janela:
    inicio: datetime
    fim: datetime
    tipo: str

    def contem(self, instante: datetime) -> bool:
        return self.inicio <= instante < self.fim


@dataclass
class EstadoReservatorio:
    nivel: float = 35.0
    esvaziando: bool = False
    vazao: float = 0.0
    brusco: bool = False
    alto_desde: datetime | None = None
    reacao_em: datetime | None = None
    eventos: list = field(default_factory=list)


def turno_de(instante: datetime) -> tuple[str, float, float]:
    hora = instante.hour
    if 6 <= hora < 14:
        return TURNOS[0][0], TURNOS[0][2], TURNOS[0][3]
    if 14 <= hora < 22:
        return TURNOS[1][0], TURNOS[1][2], TURNOS[1][3]
    return TURNOS[2][0], TURNOS[2][2], TURNOS[2][3]


def status_reservatorio(percentual: float) -> str:
    if percentual >= LIMITE_CRITICO:
        return "CRITICO"
    if percentual > LIMITE_ALTO:
        return "ALERTA_ALTO"
    if percentual < LIMITE_BAIXO:
        return "ALERTA_BAIXO"
    return "NORMAL"


def sortear_janelas(sorteio, inicio, fim, por_dia, duracao_min, duracao_max, tipo) -> list[Janela]:
    dias = (fim - inicio).total_seconds() / 86400
    quantidade = sorteio.poisson(por_dia * dias)
    janelas: list[Janela] = []
    for _ in range(quantidade * 3):
        if len(janelas) >= quantidade:
            break
        comeco = inicio + timedelta(minutes=float(sorteio.uniform(60, (fim - inicio).total_seconds() / 60 - 60)))
        duracao = timedelta(minutes=float(sorteio.uniform(duracao_min, duracao_max)))
        nova = Janela(comeco, comeco + duracao, tipo)
        sem_sobreposicao = all(nova.fim < j.inicio - timedelta(hours=2) or nova.inicio > j.fim + timedelta(hours=2)
                               for j in janelas)
        if sem_sobreposicao:
            janelas.append(nova)
    return sorted(janelas, key=lambda j: j.inicio)


def sortear_lotes(sorteio, inicio, fim) -> list[tuple[datetime, float]]:
    lotes, instante = [], inicio
    while instante < fim:
        lotes.append((instante, float(np.clip(sorteio.beta(12, 7), 0.40, 0.85))))
        instante += timedelta(hours=float(sorteio.uniform(2, 6)))
    return lotes


def fracao_metalica(lotes, instante) -> float:
    atual = lotes[0][1]
    for comeco, fracao in lotes:
        if comeco > instante:
            break
        atual = fracao
    return atual


def ler_sensores(sorteio, metalica: bool, distancia: float, deriva: bool, falha: bool):
    ruido = lambda desvio: float(sorteio.normal(0, desvio))
    if falha:
        return False, abs(ruido(0.03)), False, abs(ruido(0.03))

    if metalica:
        indutivo = 0.98 * np.exp(-0.06 * (distancia - 2.0)) + ruido(0.04)
        capacitivo = 0.15 + ruido(0.05)
        if deriva and sorteio.random() < 0.40:
            capacitivo = 0.55 + abs(ruido(0.08))
    else:
        indutivo = 0.06 + ruido(0.03)
        capacitivo = 0.95 * np.exp(-0.07 * (distancia - 2.0)) + ruido(0.04)

    indutivo, capacitivo = float(np.clip(indutivo, 0, 1)), float(np.clip(capacitivo, 0, 1))
    return indutivo >= LIMIAR_DETECCAO_INDUTIVO, indutivo, capacitivo >= LIMIAR_DETECCAO_CAPACITIVO, capacitivo


def classificar(indutivo: bool, capacitivo: bool) -> str:
    if indutivo and capacitivo:
        return "AMBAS"
    if indutivo:
        return "METALICA"
    if capacitivo:
        return "NAO_METALICA"
    return "DESCONHECIDA"


def gerar_dataset(dias: int = 7, seed: int = 42, inicio: datetime | None = None) -> pd.DataFrame:
    sorteio = np.random.default_rng(seed)
    inicio = inicio or datetime(2026, 11, 2, 0, 0, tzinfo=FUSO)
    fim = inicio + timedelta(days=dias)

    gargalos = sortear_janelas(sorteio, inicio, fim, GARGALOS_POR_DIA, 10, 40, "GARGALO")
    falhas = sortear_janelas(sorteio, inicio, fim, FALHAS_CONEXAO_POR_DIA, 5, 15, "FALHA_CONEXAO")
    derivas = sortear_janelas(sorteio, inicio, fim, DERIVAS_CAPACITIVO_NA_SEMANA / 7, 120, 240, "DERIVA_CAPACITIVO")
    bruscos = sorted(inicio + timedelta(minutes=float(m)) for m in
                     sorteio.uniform(120, dias * 1440 - 120, sorteio.poisson(ESVAZIAMENTOS_BRUSCOS_POR_DIA * dias)))
    lotes = sortear_lotes(sorteio, inicio, fim)

    reservatorio = EstadoReservatorio()
    registros = []
    instante = inicio
    rajada_restante = 0
    ultimo_turno = turno_de(instante)[0]

    def drenar(ate: datetime):
        minutos = (ate - instante).total_seconds() / 60
        if reservatorio.esvaziando and minutos > 0:
            reservatorio.nivel = max(0.0, reservatorio.nivel - reservatorio.vazao * minutos)
            limite_fim = 5.0 if reservatorio.brusco else NIVEL_FIM_ESVAZIAMENTO
            if reservatorio.nivel <= limite_fim:
                reservatorio.esvaziando, reservatorio.brusco = False, False

    def abrir_comporta(vazao: float, brusco: bool = False):
        reservatorio.esvaziando, reservatorio.vazao, reservatorio.brusco = True, vazao, brusco
        reservatorio.alto_desde, reservatorio.reacao_em = None, None

    while instante < fim:
        nome_turno, taxa, atraso_medio = turno_de(instante)
        gargalo = next((g for g in gargalos if g.contem(instante)), None)

        if gargalo:
            # Esteira parada; na liberação, ~80% das peças represadas chegam em rajada.
            drenar(gargalo.fim)
            instante = gargalo.fim
            rajada_restante = int(taxa * (gargalo.fim - gargalo.inicio).total_seconds() / 60 * 0.8)
            continue

        if rajada_restante > 0:
            intervalo = sorteio.exponential(60 / 12)
            rajada_restante -= 1
            em_rajada = True
        else:
            intervalo = sorteio.exponential(60 / taxa)
            em_rajada = False
        proximo = instante + timedelta(seconds=float(intervalo))
        drenar(proximo)
        instante = proximo
        if instante >= fim:
            break

        nome_turno, taxa, atraso_medio = turno_de(instante)
        if nome_turno != ultimo_turno:
            ultimo_turno = nome_turno
            if reservatorio.nivel > NIVEL_MIN_ESVAZIAMENTO_TURNO and not reservatorio.esvaziando:
                abrir_comporta(float(sorteio.normal(*VAZAO_COMPORTA)))

        evento = "NORMAL"
        if bruscos and instante >= bruscos[0]:
            bruscos.pop(0)
            if reservatorio.nivel > 40:
                abrir_comporta(float(sorteio.uniform(10, 15)), brusco=True)
                evento = "ESVAZIAMENTO_BRUSCO"

        metalica = sorteio.random() < fracao_metalica(lotes, instante)
        if sorteio.random() < PROB_DESALINHADA:
            distancia = float(sorteio.uniform(14, 25))
        else:
            distancia = float(np.clip(sorteio.normal(DISTANCIA_MEDIA, DISTANCIA_DESVIO), 2.0, 25.0))
        deriva = any(d.contem(instante) for d in derivas)
        falha = any(f.contem(instante) for f in falhas)
        indutivo_detectou, indutivo_intensidade, capacitivo_detectou, capacitivo_intensidade = \
            ler_sensores(sorteio, metalica, distancia, deriva, falha)
        tipo = classificar(indutivo_detectou, capacitivo_detectou)

        media, desvio = VOLUME_METALICA if metalica else VOLUME_NAO_METALICA
        reservatorio.nivel = min(100.0, reservatorio.nivel + max(0.05, float(sorteio.normal(media, desvio))))

        if not reservatorio.esvaziando:
            if reservatorio.nivel > LIMITE_ALTO and reservatorio.alto_desde is None:
                reservatorio.alto_desde = instante
                reservatorio.reacao_em = instante + timedelta(minutes=float(sorteio.exponential(atraso_medio)))
            if reservatorio.reacao_em is not None and instante >= reservatorio.reacao_em:
                abrir_comporta(float(sorteio.normal(*VAZAO_COMPORTA)))

        if evento == "NORMAL":
            if falha:
                evento = "FALHA_CONEXAO"
            elif deriva:
                evento = "DERIVA_CAPACITIVO"
            elif em_rajada:
                evento = "RAJADA_POS_GARGALO"
            elif reservatorio.brusco:
                evento = "ESVAZIAMENTO_BRUSCO"

        nivel = round(reservatorio.nivel, 2)
        registros.append({
            "timestamp": instante.isoformat(timespec="seconds"),
            "distancia_cm": round(distancia, 2),
            "indutivo_detectou_metal": indutivo_detectou,
            "indutivo_intensidade": round(indutivo_intensidade, 3),
            "capacitivo_detectou_nao_metal": capacitivo_detectou,
            "capacitivo_intensidade": round(capacitivo_intensidade, 3),
            "tipo_peca": tipo,
            "nivel_reservatorio_percentual": nivel,
            "status_reservatorio": status_reservatorio(nivel),
            "dispositivo_id": "ESP32-001",
            "versao_firmware": "1.0.0",
            "turno": nome_turno,
            "material_real": "METAL" if metalica else "NAO_METAL",
            "comporta_aberta": reservatorio.esvaziando,
            "evento_simulado": evento,
        })

    df = pd.DataFrame(registros)
    df.attrs["janelas"] = [*gargalos, *falhas, *derivas]
    return df


def salvar_eventos(df: pd.DataFrame, caminho: Path) -> None:
    linhas = [{"tipo": j.tipo, "inicio": j.inicio.isoformat(timespec="seconds"),
               "fim": j.fim.isoformat(timespec="seconds"),
               "duracao_min": round((j.fim - j.inicio).total_seconds() / 60, 1)}
              for j in sorted(df.attrs["janelas"], key=lambda j: j.inicio)]
    pd.DataFrame(linhas).to_csv(caminho, index=False)


def principal() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    analisador = argparse.ArgumentParser(description="Gera o dataset sintético da esteira MetalTech")
    analisador.add_argument("--dias", type=int, default=7, help="dias de operação simulados (padrão: 7)")
    analisador.add_argument("--seed", type=int, default=42, help="semente para reprodutibilidade (padrão: 42)")
    analisador.add_argument("--saida", type=Path, default=PASTA / "leituras_simuladas.csv")
    argumentos = analisador.parse_args()

    df = gerar_dataset(argumentos.dias, argumentos.seed)
    argumentos.saida.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(argumentos.saida, index=False)
    salvar_eventos(df, argumentos.saida.with_name("eventos_simulados.csv"))

    print(f"Dataset gerado: {len(df)} leituras em {argumentos.dias} dias -> {argumentos.saida}")
    print(f"Período: {df['timestamp'].iloc[0]} até {df['timestamp'].iloc[-1]}")
    print("\nTipos de peça (%):")
    print((df["tipo_peca"].value_counts(normalize=True) * 100).round(2).to_string())
    print("\nStatus do reservatório (%):")
    print((df["status_reservatorio"].value_counts(normalize=True) * 100).round(2).to_string())
    print("\nEventos simulados (leituras):")
    print(df["evento_simulado"].value_counts().to_string())


if __name__ == "__main__":
    principal()
