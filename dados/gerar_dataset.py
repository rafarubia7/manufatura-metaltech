"""
Gerador do dataset sintético da esteira de triagem MetalTech.

Cada linha do CSV é uma LEITURA: uma peça passando pela estação de sensores
(distância, indutivo e capacitivo) e o nível do reservatório de saída no mesmo
instante. A simulação é orientada a eventos e reproduz o funcionamento da linha,
não ruído independente:

- chegada de peças como processo de Poisson com taxa diferente por turno;
- lotes de produção que mudam o mix metálica / não metálica;
- intensidade dos sensores decaindo com a distância (peças fora do alcance
  viram DESCONHECIDA);
- reservatório enchendo gradativamente a cada peça e esvaziado por comporta
  controlada quando o operador reage ao alerta alto (com atraso por turno)
  ou na troca de turno;
- anomalias injetadas e rotuladas em `evento_simulado` (verdade-terreno):
  atolamento (gargalo) seguido de rajada, deriva do sensor capacitivo,
  falha de conexão dos sensores e esvaziamento brusco.

Uso:
    python gerar_dataset.py                 # 7 dias, seed 42
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
FUSO = timezone(timedelta(hours=-3))  # horário de Brasília

# ---------------------------------------------------------------------------
# Parâmetros da simulação (todos documentados no relatório técnico)
# ---------------------------------------------------------------------------

# Turnos: (nome, hora inicial, taxa média de peças por minuto, atraso médio do operador em min)
TURNOS = [
    ("T1", 6, 1.30, 15.0),   # 06h-14h: produção plena, equipe completa
    ("T2", 14, 1.10, 20.0),  # 14h-22h: produção levemente menor
    ("T3", 22, 0.60, 45.0),  # 22h-06h: produção reduzida, equipe enxuta (reação lenta)
]

LIMITE_BAIXO, LIMITE_ALTO, LIMITE_CRITICO = 20.0, 80.0, 95.0  # mesma regra do backend

VOLUME_METALICA = (0.40, 0.05)      # % do reservatório por peça (média, desvio)
VOLUME_NAO_METALICA = (0.46, 0.06)  # peças não metálicas são mais volumosas
VAZAO_COMPORTA = (2.0, 0.3)         # %/min de esvaziamento controlado
NIVEL_FIM_ESVAZIAMENTO = 12.0       # comporta fecha abaixo deste nível
NIVEL_MIN_ESVAZIAMENTO_TURNO = 50.0  # na troca de turno, esvazia se estiver acima

DISTANCIA_MEDIA, DISTANCIA_DESVIO = 8.0, 2.0  # cm entre peça e sensores
PROB_DESALINHADA = 0.04                      # peça torta/deslocada na esteira
LIMIAR_DETECCAO_INDUTIVO = 0.50
LIMIAR_DETECCAO_CAPACITIVO = 0.45

# Frequência das anomalias (eventos por dia)
GARGALOS_POR_DIA = 2.0
FALHAS_CONEXAO_POR_DIA = 1.0
ESVAZIAMENTOS_BRUSCOS_POR_DIA = 0.5
DERIVAS_CAPACITIVO_NA_SEMANA = 2


@dataclass
class Janela:
    inicio: datetime
    fim: datetime
    tipo: str

    def contem(self, t: datetime) -> bool:
        return self.inicio <= t < self.fim


@dataclass
class EstadoReservatorio:
    nivel: float = 35.0
    esvaziando: bool = False
    vazao: float = 0.0
    brusco: bool = False
    alto_desde: datetime | None = None
    reacao_em: datetime | None = None
    eventos: list = field(default_factory=list)


def turno_de(t: datetime) -> tuple[str, float, float]:
    h = t.hour
    if 6 <= h < 14:
        return TURNOS[0][0], TURNOS[0][2], TURNOS[0][3]
    if 14 <= h < 22:
        return TURNOS[1][0], TURNOS[1][2], TURNOS[1][3]
    return TURNOS[2][0], TURNOS[2][2], TURNOS[2][3]


def status_reservatorio(p: float) -> str:
    if p >= LIMITE_CRITICO:
        return "CRITICO"
    if p > LIMITE_ALTO:
        return "ALERTA_ALTO"
    if p < LIMITE_BAIXO:
        return "ALERTA_BAIXO"
    return "NORMAL"


def sortear_janelas(rng, inicio, fim, por_dia, dur_min, dur_max, tipo) -> list[Janela]:
    """Janelas de anomalia distribuídas uniformemente no período, sem sobreposição."""
    dias = (fim - inicio).total_seconds() / 86400
    n = rng.poisson(por_dia * dias)
    janelas: list[Janela] = []
    for _ in range(n * 3):
        if len(janelas) >= n:
            break
        t0 = inicio + timedelta(minutes=float(rng.uniform(60, (fim - inicio).total_seconds() / 60 - 60)))
        dur = timedelta(minutes=float(rng.uniform(dur_min, dur_max)))
        nova = Janela(t0, t0 + dur, tipo)
        if all(nova.fim < j.inicio - timedelta(hours=2) or nova.inicio > j.fim + timedelta(hours=2) for j in janelas):
            janelas.append(nova)
    return sorted(janelas, key=lambda j: j.inicio)


def sortear_lotes(rng, inicio, fim) -> list[tuple[datetime, float]]:
    """Lotes de produção de 2 a 6 horas, cada um com sua fração de peças metálicas."""
    lotes, t = [], inicio
    while t < fim:
        lotes.append((t, float(np.clip(rng.beta(12, 7), 0.40, 0.85))))  # média ~0,63
        t += timedelta(hours=float(rng.uniform(2, 6)))
    return lotes


def fracao_metalica(lotes, t) -> float:
    atual = lotes[0][1]
    for t0, frac in lotes:
        if t0 > t:
            break
        atual = frac
    return atual


def ler_sensores(rng, metalica: bool, distancia: float, deriva: bool, falha: bool):
    """Intensidade normalizada (0 a 1) decaindo exponencialmente com a distância."""
    ruido = lambda s: float(rng.normal(0, s))
    if falha:
        # Cabo/conector com mau contato: sinal praticamente nulo nos dois sensores
        return False, abs(ruido(0.03)), False, abs(ruido(0.03))

    if metalica:
        ind = 0.98 * np.exp(-0.06 * (distancia - 2.0)) + ruido(0.04)
        cap = 0.15 + ruido(0.05)
        if deriva and rng.random() < 0.40:
            # Sensibilidade do capacitivo desregulada: passa a "ver" metal
            cap = 0.55 + abs(ruido(0.08))
    else:
        ind = 0.06 + ruido(0.03)
        cap = 0.95 * np.exp(-0.07 * (distancia - 2.0)) + ruido(0.04)

    ind, cap = float(np.clip(ind, 0, 1)), float(np.clip(cap, 0, 1))
    return ind >= LIMIAR_DETECCAO_INDUTIVO, ind, cap >= LIMIAR_DETECCAO_CAPACITIVO, cap


def classificar(indutivo: bool, capacitivo: bool) -> str:
    if indutivo and capacitivo:
        return "AMBAS"
    if indutivo:
        return "METALICA"
    if capacitivo:
        return "NAO_METALICA"
    return "DESCONHECIDA"


def gerar_dataset(dias: int = 7, seed: int = 42, inicio: datetime | None = None) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    inicio = inicio or datetime(2026, 11, 2, 0, 0, tzinfo=FUSO)  # segunda-feira
    fim = inicio + timedelta(days=dias)

    gargalos = sortear_janelas(rng, inicio, fim, GARGALOS_POR_DIA, 10, 40, "GARGALO")
    falhas = sortear_janelas(rng, inicio, fim, FALHAS_CONEXAO_POR_DIA, 5, 15, "FALHA_CONEXAO")
    derivas = sortear_janelas(rng, inicio, fim, DERIVAS_CAPACITIVO_NA_SEMANA / 7, 120, 240, "DERIVA_CAPACITIVO")
    bruscos = sorted(inicio + timedelta(minutes=float(m)) for m in
                     rng.uniform(120, dias * 1440 - 120, rng.poisson(ESVAZIAMENTOS_BRUSCOS_POR_DIA * dias)))
    lotes = sortear_lotes(rng, inicio, fim)

    res = EstadoReservatorio()
    registros = []
    t = inicio
    rajada_restante = 0
    ultimo_turno = turno_de(t)[0]

    def drenar(ate: datetime):
        """Aplica a vazão da comporta durante o intervalo desde a última leitura."""
        nonlocal t
        dt = (ate - t).total_seconds() / 60
        if res.esvaziando and dt > 0:
            res.nivel = max(0.0, res.nivel - res.vazao * dt)
            limite_fim = 5.0 if res.brusco else NIVEL_FIM_ESVAZIAMENTO
            if res.nivel <= limite_fim:
                res.esvaziando, res.brusco = False, False

    def abrir_comporta(vazao: float, brusco: bool = False):
        res.esvaziando, res.vazao, res.brusco = True, vazao, brusco
        res.alto_desde, res.reacao_em = None, None

    while t < fim:
        nome_turno, taxa, atraso_medio = turno_de(t)
        gargalo = next((g for g in gargalos if g.contem(t)), None)

        if gargalo:
            # Esteira parada: nenhuma peça chega até o fim do atolamento
            proximo = gargalo.fim
            drenar(proximo)
            t = proximo
            # A liberação despeja o acúmulo em rajada (~80% do que deveria ter passado)
            rajada_restante = int(taxa * (gargalo.fim - gargalo.inicio).total_seconds() / 60 * 0.8)
            continue

        if rajada_restante > 0:
            intervalo = rng.exponential(60 / 12)  # ~12 peças/min durante a rajada
            rajada_restante -= 1
            evento_rajada = True
        else:
            intervalo = rng.exponential(60 / taxa)
            evento_rajada = False
        proximo = t + timedelta(seconds=float(intervalo))
        drenar(proximo)
        t = proximo
        if t >= fim:
            break

        # Troca de turno: esvaziamento programado se o reservatório estiver acima da metade
        nome_turno, taxa, atraso_medio = turno_de(t)
        if nome_turno != ultimo_turno:
            ultimo_turno = nome_turno
            if res.nivel > NIVEL_MIN_ESVAZIAMENTO_TURNO and not res.esvaziando:
                abrir_comporta(float(rng.normal(*VAZAO_COMPORTA)))

        # Esvaziamento brusco (descarga manual indevida, fora do procedimento)
        evento = "NORMAL"
        if bruscos and t >= bruscos[0]:
            bruscos.pop(0)
            if res.nivel > 40:
                abrir_comporta(float(rng.uniform(10, 15)), brusco=True)
                evento = "ESVAZIAMENTO_BRUSCO"

        # Peça e sensores
        metalica = rng.random() < fracao_metalica(lotes, t)
        if rng.random() < PROB_DESALINHADA:
            distancia = float(rng.uniform(14, 25))
        else:
            distancia = float(np.clip(rng.normal(DISTANCIA_MEDIA, DISTANCIA_DESVIO), 2.0, 25.0))
        deriva = any(d.contem(t) for d in derivas)
        falha = any(f.contem(t) for f in falhas)
        ind_det, ind_int, cap_det, cap_int = ler_sensores(rng, metalica, distancia, deriva, falha)
        tipo = classificar(ind_det, cap_det)

        # A peça cai no reservatório (todas as peças triadas vão para o mesmo coletor)
        media, desvio = VOLUME_METALICA if metalica else VOLUME_NAO_METALICA
        res.nivel = min(100.0, res.nivel + max(0.05, float(rng.normal(media, desvio))))

        # Operador: percebe o alerta alto e abre a comporta após um atraso aleatório
        if not res.esvaziando:
            if res.nivel > LIMITE_ALTO and res.alto_desde is None:
                res.alto_desde = t
                res.reacao_em = t + timedelta(minutes=float(rng.exponential(atraso_medio)))
            if res.reacao_em is not None and t >= res.reacao_em:
                abrir_comporta(float(rng.normal(*VAZAO_COMPORTA)))

        if evento == "NORMAL":
            if falha:
                evento = "FALHA_CONEXAO"
            elif deriva:
                evento = "DERIVA_CAPACITIVO"
            elif evento_rajada:
                evento = "RAJADA_POS_GARGALO"
            elif res.brusco:
                evento = "ESVAZIAMENTO_BRUSCO"

        nivel = round(res.nivel, 2)
        registros.append({
            "timestamp": t.isoformat(timespec="seconds"),
            "distancia_cm": round(distancia, 2),
            "indutivo_detectou_metal": ind_det,
            "indutivo_intensidade": round(ind_int, 3),
            "capacitivo_detectou_nao_metal": cap_det,
            "capacitivo_intensidade": round(cap_int, 3),
            "tipo_peca": tipo,
            "nivel_reservatorio_percentual": nivel,
            "status_reservatorio": status_reservatorio(nivel),
            "dispositivo_id": "ESP32-001",
            "versao_firmware": "1.0.0",
            "turno": nome_turno,
            "material_real": "METAL" if metalica else "NAO_METAL",
            "comporta_aberta": res.esvaziando,
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


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Gera o dataset sintético da esteira MetalTech")
    parser.add_argument("--dias", type=int, default=7, help="dias de operação simulados (padrão: 7)")
    parser.add_argument("--seed", type=int, default=42, help="semente para reprodutibilidade (padrão: 42)")
    parser.add_argument("--saida", type=Path, default=PASTA / "leituras_simuladas.csv")
    args = parser.parse_args()

    df = gerar_dataset(args.dias, args.seed)
    args.saida.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(args.saida, index=False)
    salvar_eventos(df, args.saida.with_name("eventos_simulados.csv"))

    print(f"Dataset gerado: {len(df)} leituras em {args.dias} dias -> {args.saida}")
    print(f"Período: {df['timestamp'].iloc[0]} até {df['timestamp'].iloc[-1]}")
    print("\nTipos de peça (%):")
    print((df["tipo_peca"].value_counts(normalize=True) * 100).round(2).to_string())
    print("\nStatus do reservatório (%):")
    print((df["status_reservatorio"].value_counts(normalize=True) * 100).round(2).to_string())
    print("\nEventos simulados (leituras):")
    print(df["evento_simulado"].value_counts().to_string())


if __name__ == "__main__":
    main()
