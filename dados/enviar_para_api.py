"""
Integração CIDA -> API REST do MetalTech.

Lê o dataset sintético, envia cada leitura via POST /api/v1/leituras no contrato
definido no plano de ação técnico (seção 1) e, ao final, confronta os indicadores
devolvidos pela API (GET /estatisticas, /alertas, /leituras) com os calculados
localmente pelo pandas. O resultado fica em dados/resultados/validacao_integracao.json.

Pré-requisito: backend rodando (ver docs/guia_execucao.md) com o banco vazio.

Uso:
    python enviar_para_api.py
    python enviar_para_api.py --limite 500 --url http://localhost:8080
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from collections import Counter
from pathlib import Path

import pandas as pd
import requests

from analise_estatistica import simular_alertas

PASTA = Path(__file__).resolve().parent
TOLERANCIA = 0.01  # diferença absoluta aceita nas métricas de ponto flutuante


def montar_payload(linha: pd.Series, indice: int) -> dict:
    ts = pd.Timestamp(linha["timestamp"]).tz_convert("UTC")
    ts_iso = ts.strftime("%Y-%m-%dT%H:%M:%SZ")
    sem_sinal = linha["indutivo_intensidade"] < 0.1 and linha["capacitivo_intensidade"] < 0.1
    status_sensor = "SEM_SINAL" if sem_sinal else "OK"
    nivel = float(linha["nivel_reservatorio_percentual"])
    return {
        "timestamp": ts_iso,
        "sensorDistancia": {"valor": float(linha["distancia_cm"]), "unidade": "cm", "status": "OK"},
        "sensorIndutivo": {"detectouMetal": bool(linha["indutivo_detectou_metal"]),
                           "intensidade": float(linha["indutivo_intensidade"]), "status": status_sensor},
        "sensorCapacitivo": {"detectouNaoMetal": bool(linha["capacitivo_detectou_nao_metal"]),
                             "intensidade": float(linha["capacitivo_intensidade"]), "status": status_sensor},
        "peca": {"id": f"peca-{ts:%Y%m%d-%H%M%S}-{indice:05d}", "tipo": linha["tipo_peca"], "timestampDetectado": ts_iso},
        "nivelReservatorio": {"nivelAtual": nivel, "capacidadeMaxima": 100.0, "percentual": nivel,
                              "status": linha["status_reservatorio"]},
        "dispositivoId": linha["dispositivo_id"],
        "versaoFirmware": str(linha["versao_firmware"]),
    }


def enviar(sessao: requests.Session, url: str, df: pd.DataFrame, atraso: float) -> dict:
    codigos: Counter = Counter()
    falhas = []
    divergencias_status = 0
    inicio = time.perf_counter()
    for i, (_, linha) in enumerate(df.iterrows()):
        try:
            r = sessao.post(f"{url}/api/v1/leituras", json=montar_payload(linha, i), timeout=30)
            codigos[r.status_code] += 1
            if r.status_code == 201:
                if r.json()["nivelReservatorio"]["status"] != linha["status_reservatorio"]:
                    divergencias_status += 1
            elif len(falhas) < 10:
                falhas.append({"indice": i, "status": r.status_code, "corpo": r.text[:300]})
        except requests.RequestException as e:
            codigos["erro_conexao"] += 1
            if len(falhas) < 10:
                falhas.append({"indice": i, "erro": str(e)})
        if (i + 1) % 1000 == 0 or i + 1 == len(df):
            print(f"  {i + 1}/{len(df)} enviadas ({time.perf_counter() - inicio:.0f}s)")
        if atraso:
            time.sleep(atraso)
    duracao = time.perf_counter() - inicio
    return {
        "enviadas": len(df),
        "codigos_http": {str(k): v for k, v in codigos.items()},
        "sucesso_pct": round(100 * codigos.get(201, 0) / len(df), 2),
        "duracao_s": round(duracao, 1),
        "leituras_por_segundo": round(len(df) / duracao, 1),
        "status_reservatorio_divergente_do_csv": divergencias_status,
        "exemplos_de_falha": falhas,
    }


def comparar(nome: str, local, api, tolerancia: float = 0.0) -> dict:
    if isinstance(local, (int, float)) and isinstance(api, (int, float)):
        ok = abs(float(local) - float(api)) <= tolerancia
    else:
        ok = local == api
    return {"metrica": nome, "local": local, "api": api, "ok": bool(ok)}


def validar(sessao: requests.Session, url: str, df: pd.DataFrame) -> list[dict]:
    est = sessao.get(f"{url}/api/v1/leituras/estatisticas", timeout=120).json()
    alertas_api = sessao.get(f"{url}/api/v1/leituras/alertas", timeout=60).json()
    total_api = sessao.get(f"{url}/api/v1/leituras", params={"size": 1}, timeout=60).json()["totalElements"]

    nivel = df["nivel_reservatorio_percentual"]
    tipos = df["tipo_peca"].value_counts()
    status = df["status_reservatorio"].value_counts()
    prep = df.assign(timestamp=pd.to_datetime(df["timestamp"], utc=True, format="ISO8601"))
    alertas_local = simular_alertas(prep.sort_values("timestamp", kind="stable"))

    checagens = [
        comparar("total de leituras (GET /leituras)", len(df), total_api),
        comparar("totalLeituras (estatísticas)", len(df), est["totalLeituras"]),
        comparar("totalMetalicas", int(tipos.get("METALICA", 0)), est["totalMetalicas"]),
        comparar("totalNaoMetalicas", int(tipos.get("NAO_METALICA", 0)), est["totalNaoMetalicas"]),
        comparar("totalDesconhecidas", int(tipos.get("DESCONHECIDA", 0)), est["totalDesconhecidas"]),
        comparar("totalAmbas", int(tipos.get("AMBAS", 0)), est["totalAmbas"]),
        comparar("média do nível", round(nivel.mean(), 4), round(est["mediaNivelReservatorio"], 4), TOLERANCIA),
        comparar("mediana do nível", round(nivel.median(), 4), round(est["medianaNivelReservatorio"], 4), TOLERANCIA),
        comparar("desvio padrão do nível (amostral)", round(nivel.std(), 4), round(est["desvioPadraoNivelReservatorio"], 4), TOLERANCIA),
        comparar("Q1 do nível", round(nivel.quantile(0.25), 4), round(est["quartil1NivelReservatorio"], 4), TOLERANCIA),
        comparar("Q3 do nível", round(nivel.quantile(0.75), 4), round(est["quartil3NivelReservatorio"], 4), TOLERANCIA),
        comparar("mínimo do nível", float(nivel.min()), est["minimoNivelReservatorio"], TOLERANCIA),
        comparar("máximo do nível", float(nivel.max()), est["maximoNivelReservatorio"], TOLERANCIA),
    ]
    for s in ["NORMAL", "ALERTA_BAIXO", "ALERTA_ALTO", "CRITICO"]:
        checagens.append(comparar(f"leituras com status {s}", int(status.get(s, 0)),
                                  est["distribuicaoStatusReservatorio"][s]["quantidade"]))
    for tipo, qtd in alertas_local["alertas_por_tipo"].items():
        checagens.append(comparar(f"alertas gerados {tipo}", qtd, est["alertasPorTipo"][tipo]))
    ativos_esperados = alertas_local["alertas_ativos_ao_final"] + alertas_local["alertas_por_tipo"]["CONTAGEM_ANOMALA"]
    checagens.append(comparar("alertas ativos (GET /alertas)", ativos_esperados, len(alertas_api)))
    return checagens


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Envia o dataset sintético para a API e valida a integração")
    parser.add_argument("--csv", type=Path, default=PASTA / "leituras_simuladas.csv")
    parser.add_argument("--url", default="http://localhost:8080")
    parser.add_argument("--limite", type=int, default=None, help="envia apenas as N primeiras leituras")
    parser.add_argument("--atraso", type=float, default=0.0, help="pausa em segundos entre requisições")
    args = parser.parse_args()

    df = pd.read_csv(args.csv)
    if args.limite:
        df = df.head(args.limite)

    sessao = requests.Session()
    try:
        existentes = sessao.get(f"{args.url}/api/v1/leituras", params={"size": 1}, timeout=10).json()["totalElements"]
    except requests.RequestException as e:
        sys.exit(f"API indisponível em {args.url}: {e}\nSuba o backend antes (docs/guia_execucao.md).")
    if existentes:
        print(f"ATENÇÃO: o banco já tem {existentes} leituras; a validação compara o total e vai acusar diferença.\n"
              "Para recomeçar do zero: docker compose down -v && docker compose up -d postgres")

    print(f"Enviando {len(df)} leituras para {args.url} ...")
    envio = enviar(sessao, args.url, df, args.atraso)
    print(f"Envio concluído: {envio['codigos_http']} em {envio['duracao_s']}s ({envio['leituras_por_segundo']} leituras/s)")

    checagens = validar(sessao, args.url, df)
    aprovadas = sum(c["ok"] for c in checagens)
    resultado = {
        "url": args.url,
        "leituras_ja_existentes_antes_do_envio": existentes,
        "envio": envio,
        "validacao": {"aprovadas": aprovadas, "total": len(checagens), "checagens": checagens},
    }
    destino = PASTA / "resultados" / "validacao_integracao.json"
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\nValidação API x pandas: {aprovadas}/{len(checagens)} checagens aprovadas")
    for c in checagens:
        print(f"  [{'OK' if c['ok'] else 'FALHOU'}] {c['metrica']}: local={c['local']} api={c['api']}")
    print(f"\nRelatório salvo em {destino}")
    sys.exit(0 if aprovadas == len(checagens) else 1)


if __name__ == "__main__":
    main()
