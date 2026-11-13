#!/usr/bin/env python3
"""Gateway IO-Link -> API MetalTech.

Le os 3 sensores do mestre IO-Link (GET local) e envia um POST por peca
para /api/v1/leituras. Rodar no PC do laboratorio.

        python gateway.py --sondar     # mostra a resposta crua de cada porta
        python gateway.py --sondar --continuo   # repete a cada segundo (passe uma peça para ver mudar)
        python gateway.py --dry-run    # roda tudo, imprime o JSON em vez de enviar
        python gateway.py              # producao
"""
import argparse
import http.client
import json
import logging
import queue
import statistics
import threading
import time
import uuid
from collections import deque
from datetime import datetime, timezone
from urllib.parse import urlparse

import requests

log = logging.getLogger("gateway")


def agora_iso():
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


class Mestre:
    """Cliente do mestre IO-Link (GET passivo).

    Usa http.client de proposito: requests/urllib3 trocam os colchetes de
    port[1] por %5B1%5D, e o servidor embarcado do mestre pode nao decodificar.
    """

    def __init__(self, base_url, timeout=2.0):
        u = urlparse(base_url)
        self.host, self.porta_http, self.timeout = u.hostname, u.port or 80, timeout
        self.conn = None

    def ler_bruto(self, porta):
        caminho = f"/iolinkmaster/port[{porta}]/iolinkdevice/pdin/getdata"
        for tentativa in (1, 2):  # a 2a tentativa reabre uma conexao keep-alive caida
            try:
                if self.conn is None:
                    self.conn = http.client.HTTPConnection(self.host, self.porta_http, timeout=self.timeout)
                self.conn.request("GET", caminho)
                resp = self.conn.getresponse()
                corpo = resp.read()
                if resp.status != 200:
                    raise OSError(f"HTTP {resp.status} em {caminho}")
                return json.loads(corpo)
            except (OSError, http.client.HTTPException):
                self.conn.close()
                self.conn = None
                if tentativa == 2:
                    raise

    def ler_inteiro(self, porta):
        corpo = self.ler_bruto(porta)
        if corpo.get("code") not in (200, "200") or "data" not in corpo:
            raise ValueError(f"IO-Link code {corpo.get('code')} na porta {porta}")
        return int(corpo["data"]["value"], 16)


def liga_capacitivo(ativo, v, c):
    """Histerese: valor ALTO = objeto presente."""
    if v >= c["liga_acima"]:
        return True
    if v < c["desliga_abaixo"]:
        return False
    return ativo


def liga_indutivo(ativo, v, c):
    """Histerese: valor BAIXO = metal presente (livre = valor alto, ex. 32736)."""
    if v <= c["liga_abaixo"]:
        return True
    if v > c["desliga_acima"]:
        return False
    return ativo


def nivel_percentual(raw, vazio_raw, cheio_raw):
    pct = (vazio_raw - raw) / (vazio_raw - cheio_raw) * 100.0
    return max(0.0, min(100.0, pct))  # a API rejeita negativo (@PositiveOrZero)


def status_nivel(pct, cfg_nivel):
    """Nomes devem ser exatamente os do enum StatusReservatorio do backend (mesmas faixas:
    critico >= limite_critico, alto > limite_atencao, baixo < limite_baixo)."""
    nomes = cfg_nivel["nomes"]
    if pct >= cfg_nivel["limite_critico"]:
        return nomes["critico"]
    if pct > cfg_nivel["limite_atencao"]:
        return nomes["atencao"]
    if cfg_nivel.get("limite_baixo") is not None and pct < cfg_nivel["limite_baixo"]:
        return nomes["baixo"]
    return nomes["normal"]


def classificar(viu_metal, viu_nao_metal):
    # O capacitivo tambem reage a metal (nos seus dados: 7.5k-9.5k contra
    # 1.9k-6.7k no nao metal), entao quem decide "metalica" e o indutivo.
    if viu_metal:
        return "METALICA"
    if viu_nao_metal:
        return "NAO_METALICA"
    return "DESCONHECIDA"


def montar_payload(cfg, ts, tipo, dist_cm, ind, cap, pct):
    return {
        "timestamp": ts,
        "sensorDistancia": {"valor": round(dist_cm, 2), "unidade": "cm", "status": "OK"},
        "sensorIndutivo": {"detectouMetal": ind, "intensidade": None, "status": "OK"},
        "sensorCapacitivo": {"detectouNaoMetal": cap, "intensidade": None, "status": "OK"},
        "peca": {"id": str(uuid.uuid4()), "tipo": tipo, "timestampDetectado": ts},
        "nivelReservatorio": {
            "nivelAtual": round(pct, 1),
            "capacidadeMaxima": 100.0,
            "percentual": round(pct, 1),
            "status": status_nivel(pct, cfg["nivel"]),
        },
        "dispositivoId": cfg["dispositivo_id"],
        "versaoFirmware": cfg.get("versao_firmware"),
    }


class Enviador(threading.Thread):
    """Envia a fila para a API sem travar a leitura dos sensores."""

    MAX_TENTATIVAS_5XX = 5

    def __init__(self, cfg, fila, dry_run):
        super().__init__(daemon=True)
        self.url = cfg["api_url"]
        self.fila = fila
        self.dry_run = dry_run
        self.sessao = requests.Session()
        self.headers = {"Content-Type": "application/json"}
        chave = cfg.get("api_key") or cfg.get("api_token")  # api_token: nome antigo, ainda aceito
        if chave:
            self.headers["X-API-Key"] = chave  # mesmo cabeçalho que o ApiKeyFilter do backend valida

    def run(self):
        espera = 1.0
        while True:
            payload = self.fila.get()
            tentativas_5xx = 0
            while True:
                if self.dry_run:
                    print("=== Payload dry-run ===")
                    print(json.dumps(payload, ensure_ascii=False, indent=2), flush=True)
                    break
                try:
                    r = self.sessao.post(self.url, json=payload, headers=self.headers, timeout=5)
                    if r.status_code < 300:
                        log.info("enviado: %s | nivel %.1f%% | HTTP %s", payload["peca"]["tipo"],
                                 payload["nivelReservatorio"]["percentual"], r.status_code)
                        espera = 1.0
                        break
                    if r.status_code < 500:  # 4xx: reenviar nao resolve
                        log.error("API recusou (%s): %s | payload=%s",
                                r.status_code, r.text[:300], json.dumps(payload))
                        break
                    tentativas_5xx += 1
                    log.warning("API respondeu %s (tentativa %d)", r.status_code, tentativas_5xx)
                    if tentativas_5xx >= self.MAX_TENTATIVAS_5XX:
                        log.error("descartando payload apos %d erros 5xx: %s",
                                tentativas_5xx, json.dumps(payload))
                        break
                except requests.RequestException as e:  # rede fora: tenta para sempre
                    log.warning("falha ao enviar: %s", e)
                time.sleep(espera)
                espera = min(espera * 2, 30.0)


def validar_config(cfg, dry_run):
    d = cfg["distancia"]
    if d.get("vazio_raw") is None or d.get("cheio_raw") is None or d["vazio_raw"] == d["cheio_raw"]:
        raise SystemExit("config: preencha distancia.vazio_raw e distancia.cheio_raw (leituras --sondar da porta de distancia).")
    if not dry_run and "SUA-API" in cfg["api_url"]:
        raise SystemExit("config: preencha api_url.")


def executar(cfg, dry_run):
    validar_config(cfg, dry_run)
    mestre = Mestre(cfg["mestre_url"])
    log.info("gateway iniciado%s | mestre=%s | api=%s", " (DRY-RUN: nada sera enviado)" if dry_run else "",
             cfg["mestre_url"], cfg["api_url"])
    log.info("aguardando pecas nos sensores (so envia quando uma peca passa; Ctrl+C para parar)")
    portas, sens, dist_cfg = cfg["portas"], cfg["sensores"], cfg["distancia"]
    janela = cfg["classificacao"]["janela_s"]
    dt = cfg["intervalo_leitura_s"]
    heartbeat = cfg.get("heartbeat_s", 0)

    fila = queue.Queue(maxsize=5000)
    Enviador(cfg, fila, dry_run).start()

    def enfileirar(payload):
        try:
            fila.put_nowait(payload)
        except queue.Full:
            log.error("fila cheia, descartando leitura")

    falha = {}  # porta -> bool; loga so quando muda (a porta 1 chegou a falhar em 55% das leituras)

    def ler(porta):
        try:
            v = mestre.ler_inteiro(porta)
            if falha.get(porta):
                log.info("porta %s voltou a responder", porta)
                falha[porta] = False
            return v
        except (OSError, http.client.HTTPException, ValueError) as e:
            if not falha.get(porta):
                log.warning("porta %s falhou: %s", porta, e)
                falha[porta] = True
            return None

    historico = deque(maxlen=dist_cfg["amostras_mediana"])
    ind = cap = False
    estado = "livre"  # livre -> coletando -> aguardando (sensores precisam liberar)
    t_ini = ts_ini = None
    viu_m = viu_n = False
    ultimo_envio = time.monotonic()
    avisou_sem_nivel = False

    while True:
        t0 = time.monotonic()
        v_cap = ler(portas["capacitivo"])
        v_ind = ler(portas["indutivo"])
        v_dist = ler(portas["distancia"])

        # falha em uma porta nao derruba as outras: mantem o ultimo estado/valor
        if v_dist is not None:
            historico.append(v_dist)
        if v_cap is not None:
            cap = liga_capacitivo(cap, v_cap, sens["capacitivo"])
        if v_ind is not None:
            ind = liga_indutivo(ind, v_ind, sens["indutivo"])

        if not historico:  # a API exige nivel em todo POST; espera a 1a leitura valida
            if not avisou_sem_nivel:
                log.warning("aguardando a primeira leitura valida da porta de distancia")
                avisou_sem_nivel = True
            time.sleep(1)
            continue
        if v_cap is None and v_ind is None:
            time.sleep(max(0.0, dt - (time.monotonic() - t0)))
            continue

        dist_raw = statistics.median(historico)
        pct = nivel_percentual(dist_raw, dist_cfg["vazio_raw"], dist_cfg["cheio_raw"])
        dist_cm = max(0.0, dist_raw * dist_cfg["cm_por_unidade"] + dist_cfg.get("cm_offset", 0.0))
        detectando = ind or cap

        if estado == "livre" and detectando:
            estado, t_ini, ts_ini = "coletando", t0, agora_iso()
            viu_m, viu_n = ind, cap
            log.info("peca detectada; classificando por %.1f s...", janela)
        elif estado == "coletando":
            viu_m |= ind
            viu_n |= cap
            if t0 - t_ini >= janela:
                tipo = classificar(viu_m, viu_n)
                log.info("classificada: %s (na janela: indutivo=%s capacitivo=%s)", tipo, viu_m, viu_n)
                enfileirar(montar_payload(cfg, ts_ini, tipo, dist_cm, viu_m, viu_n, pct))
                ultimo_envio = t0
                estado = "aguardando"
        elif estado == "aguardando" and not detectando:
            estado = "livre"
        elif estado == "livre" and heartbeat and t0 - ultimo_envio >= heartbeat:
            enfileirar(montar_payload(cfg, agora_iso(), "DESCONHECIDA", dist_cm, False, False, pct))
            ultimo_envio = t0

        time.sleep(max(0.0, dt - (time.monotonic() - t0)))


def sondar(cfg, continuo=False):
    """Mostra a leitura crua de cada porta. Com --continuo, repete a cada segundo (Ctrl+C para sair)."""
    mestre = Mestre(cfg["mestre_url"])
    sens = cfg["sensores"]
    ind = cap = False
    while True:
        if not continuo:
            for nome, porta in cfg["portas"].items():
                try:
                    bruto = mestre.ler_bruto(porta)
                    hx = bruto["data"]["value"]
                    print(f"porta {porta} ({nome}): hex={hx} int={int(hx, 16)} resposta={json.dumps(bruto)}")
                except Exception as e:  # diagnostico: mostrar qualquer erro
                    print(f"porta {porta} ({nome}): ERRO {e}")
            return
        partes = []
        for nome, porta in cfg["portas"].items():
            try:
                v = mestre.ler_inteiro(porta)
            except Exception as e:
                partes.append(f"{nome}=ERRO({e})")
                continue
            if nome == "indutivo":
                ind = liga_indutivo(ind, v, sens["indutivo"])
                partes.append(f"indutivo={v} ({'METAL' if ind else 'livre'})")
            elif nome == "capacitivo":
                cap = liga_capacitivo(cap, v, sens["capacitivo"])
                partes.append(f"capacitivo={v} ({'PECA' if cap else 'livre'})")
            else:
                partes.append(f"{nome}={v}")
        print(time.strftime("%H:%M:%S"), " | ".join(partes), flush=True)
        try:
            time.sleep(1)
        except KeyboardInterrupt:
            return


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--config", default="config.json")
    ap.add_argument("--sondar", action="store_true")
    ap.add_argument("--continuo", action="store_true", help="com --sondar: repete a leitura a cada segundo")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    with open(args.config, encoding="utf-8") as f:
        cfg = json.load(f)
    if args.sondar:
        try:
            sondar(cfg, args.continuo)
        except KeyboardInterrupt:
            pass
    else:
        try:
            executar(cfg, args.dry_run)
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()