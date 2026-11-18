#!/usr/bin/env python3
"""Gateway IO-Link -> API MetalTech.

Le os 3 sensores do mestre IO-Link (GET local) e envia um POST por peca
para /api/v1/leituras. Rodar no PC do laboratorio.

        python gateway.py --sondar     # mostra a resposta crua de cada porta
        python gateway.py --dry-run    # roda tudo, imprime o JSON em vez de enviar
        python gateway.py              # producao
"""
import argparse
import http.client
import json
import logging
import os
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

    def __init__(self, base_url, timeout=2.0, sensor_urls=None):
        u = urlparse(base_url)
        self.host, self.porta_http, self.timeout = u.hostname, u.port or 80, timeout
        self.sensor_urls = sensor_urls or {}
        self.sessao = requests.Session()
        self.conn = None

    def ler_bruto(self, porta):
        # Quando sensor_urls existir, porta pode ser o nome ('capacitivo',
        # 'indutivo' ou 'distancia') e o gateway chama exatamente a URL REST.
        if isinstance(porta, str) and porta in self.sensor_urls:
            r = self.sessao.get(self.sensor_urls[porta], timeout=self.timeout)
            r.raise_for_status()
            try:
                corpo = r.json()
            except requests.JSONDecodeError:
                corpo = {"value": r.text.strip()}
            if not isinstance(corpo, dict):
                corpo = {"value": corpo}
            return corpo

        caminho = f"/iolinkmaster/port[{porta}]/iolinkdevice/pdin/getdata"
        for tentativa in (1, 2, 3):  # reabre a conexao keep-alive quando o mestre derruba (WinError 10054)
            try:
                if self.conn is None:
                    self.conn = http.client.HTTPConnection(self.host, self.porta_http, timeout=self.timeout) # busca conexão HTTP com o IO-Link Master
                self.conn.request("GET", caminho)
                resp = self.conn.getresponse()
                corpo = resp.read()
                if resp.status != 200:
                    raise OSError(f"HTTP {resp.status} em {caminho}")
                return json.loads(corpo)
            except (OSError, http.client.HTTPException):
                if self.conn is not None:
                    self.conn.close()
                self.conn = None
                if tentativa == 3:
                    raise
                time.sleep(0.02 * tentativa)

    def ler_inteiro(self, porta):
        corpo = self.ler_bruto(porta)

        dados = corpo.get("data") if isinstance(corpo, dict) else None
        if isinstance(dados, dict) and "value" in dados:
            valor = dados["value"]
        elif isinstance(corpo, dict) and "value" in corpo:
            valor = corpo["value"]
        elif corpo.get("code") not in (200, "200") if isinstance(corpo, dict) else False:
            if corpo.get("code") in (530, "530"):
                raise SemAlvo(f"sensor {porta} respondeu code 530 (sem medicao valida)")
            raise ValueError(f"sensor {porta} respondeu code {corpo.get('code')}")
        else:
            raise ValueError(f"sensor {porta} nao retornou um valor")

        if isinstance(valor, (int, float)):
            return int(valor)
        texto = str(valor).strip().lower()
        if texto.startswith("0x"):
            return int(texto, 16)
        try:
            return int(texto, 10)
        except ValueError:
            return int(texto, 16)


class SemAlvo(ValueError):
    """Mestre respondeu 530: o sensor esta ligado, mas marcou a medicao como invalida
    (no O5D150 isso acontece quando nao ha alvo dentro do alcance)."""


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


_ultimo_aviso_calibracao = float("-inf")


def nivel_percentual(raw, vazio_raw, cheio_raw):
    global _ultimo_aviso_calibracao
    pct = (vazio_raw - raw) / (vazio_raw - cheio_raw) * 100.0
    # Fora de 0-100% indica calibracao errada (vazio_raw/cheio_raw) ou sensor fora de posicao.
    # No maximo um aviso a cada 30 s: o loop roda a 10 Hz e enchia o log.
    if (pct < -5.0 or pct > 105.0) and time.monotonic() - _ultimo_aviso_calibracao >= 30.0:
        _ultimo_aviso_calibracao = time.monotonic()
        log.warning("distancia raw=%s fora da calibracao (vazio=%s, cheio=%s): nivel %.1f%% foi limitado",
                    raw, vazio_raw, cheio_raw, pct)
    return max(0.0, min(100.0, pct))  # a API rejeita negativo (@PositiveOrZero)


def status_nivel(pct, cfg_nivel):
    """Nomes devem ser exatamente os do enum StatusReservatorio do backend."""
    nomes = cfg_nivel["nomes"]
    if pct >= cfg_nivel["limite_critico"]:
        return nomes["critico"]
    if pct >= cfg_nivel["limite_atencao"]:
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


def montar_payload(cfg, ts, tipo, dist_cm, ind, cap, pct, status_dist="OK"):
    return {
        "timestamp": ts,
        "sensorDistancia": {"valor": round(dist_cm, 2), "unidade": "cm", "status": status_dist},
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
        if cfg.get("api_key"):
            self.headers["X-API-Key"] = cfg["api_key"]
        if cfg.get("api_token"):
            self.headers["Authorization"] = f"Bearer {cfg['api_token']}"
        if cfg.get("x_api_key"):
            self.headers["X-API-Key"] = cfg["x_api_key"]

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
    mestre = Mestre(cfg["mestre_url"], sensor_urls=cfg.get("sensor_urls"))
    portas, sens, dist_cfg = cfg["portas"], cfg["sensores"], cfg["distancia"]
    janela = cfg["classificacao"]["janela_s"]
    dt = cfg["intervalo_leitura_s"]  # intervalo entre leituras (nao entre envios)
    heartbeat = cfg.get("heartbeat_s", 0)

    fila = queue.Queue(maxsize=5000)
    Enviador(cfg, fila, dry_run).start()

    def enfileirar(payload):
        try:
            fila.put_nowait(payload)
        except queue.Full:
            log.error("fila cheia, descartando leitura")

    falha = {}  # porta -> bool; loga so quando muda (a porta 1 chegou a falhar em 55% das leituras)

    def ler(porta, valor_sem_alvo=None, desloca=0):
        try:
            v = mestre.ler_inteiro(porta) >> desloca
            if falha.get(porta):
                log.info("porta %s voltou a responder", porta)
                falha[porta] = False
            return v
        except SemAlvo as e:
            if valor_sem_alvo is not None:  # sem alvo no alcance = reservatorio vazio
                return valor_sem_alvo
            if not falha.get(porta):
                log.warning("porta %s falhou: %s", porta, e)
                falha[porta] = True
            return None
        except (OSError, http.client.HTTPException, ValueError) as e:
            if not falha.get(porta):
                log.warning("porta %s falhou: %s", porta, e)
                falha[porta] = True
            return None

    historico = deque(maxlen=dist_cfg["amostras_mediana"])
    sem_alvo = dist_cfg["vazio_raw"] if dist_cfg.get("sem_alvo_e_vazio") else None
    # O5D150: a palavra de 16 bits traz a distancia nos 12 bits altos e bits de status
    # nos 4 baixos (leituras sobem de 16 em 16: 0x5A1, 0x5B1, 0x5C1...). 4 = distancia em mm.
    desloca_dist = dist_cfg.get("desloca_bits", 0)
    nivel_fallback = dist_cfg.get("nivel_sem_leitura_pct")
    intervalo_dist = cfg.get("intervalo_distancia_s", 0.5)  # nivel muda devagar; sobra tempo p/ ind/cap
    liberacao = cfg["classificacao"].get("liberacao_s", 0.2)  # sensores livres por este tempo = peca saiu
    max_peca = cfg["classificacao"].get("max_s", 5.0)  # peca parada na frente: envia mesmo assim
    ind = cap = False
    estado = "livre"  # livre -> coletando -> (aguardando, so se a peca ficar parada) -> livre
    t_ini = ts_ini = t_ativo = None
    viu_m = viu_n = False
    ultimo_envio = time.monotonic()
    ultima_dist = float("-inf")
    avisou_sem_nivel = False

    def nivel_atual():
        """(pct, dist_cm, status) ou None se nao ha leitura nem fallback configurado."""
        if historico:
            raw = statistics.median(historico)
            pct = nivel_percentual(raw, dist_cfg["vazio_raw"], dist_cfg["cheio_raw"])
            cm = max(0.0, raw * dist_cfg["cm_por_unidade"] + dist_cfg.get("cm_offset", 0.0))
            return pct, cm, "OK"
        if nivel_fallback is not None:
            return float(nivel_fallback), 0.0, "SEM_LEITURA"
        return None

    def enviar(ts, tipo, m, n):
        nonlocal avisou_sem_nivel
        nivel = nivel_atual()
        if nivel is None:  # a API exige nivel em todo POST
            if not avisou_sem_nivel:
                log.error("sem leitura valida de distancia: pecas descartadas ate a porta voltar "
                          "(defina distancia.nivel_sem_leitura_pct para enviar mesmo assim)")
                avisou_sem_nivel = True
            return
        pct, cm, status = nivel
        enfileirar(montar_payload(cfg, ts, tipo, cm, m, n, pct, status))

    while True:
        t0 = time.monotonic()
        v_cap = ler(portas["capacitivo"])
        v_ind = ler(portas["indutivo"])
        if t0 - ultima_dist >= intervalo_dist:
            ultima_dist = t0
            v_dist = ler(portas["distancia"], sem_alvo, desloca_dist)
            if v_dist is not None:
                historico.append(v_dist)

        # falha em uma porta nao derruba as outras: mantem o ultimo estado
        if v_cap is not None:
            cap = liga_capacitivo(cap, v_cap, sens["capacitivo"])
        if v_ind is not None:
            ind = liga_indutivo(ind, v_ind, sens["indutivo"])
        detectando = ind or cap

        if estado == "livre":
            if detectando:
                estado, t_ini, ts_ini, t_ativo = "coletando", t0, agora_iso(), t0
                viu_m, viu_n = ind, cap
            elif heartbeat and t0 - ultimo_envio >= heartbeat:
                enviar(agora_iso(), "DESCONHECIDA", False, False)
                ultimo_envio = t0
        elif estado == "coletando":
            # Junta tudo que os dois sensores viram enquanto a peca passa: o indutivo
            # pode acionar depois do capacitivo, e antes isso virava NAO_METALICA.
            viu_m |= ind
            viu_n |= cap
            if detectando:
                t_ativo = t0
            saiu = t0 - t_ini >= janela and t0 - t_ativo >= liberacao
            if saiu or t0 - t_ini >= max_peca:
                enviar(ts_ini, classificar(viu_m, viu_n), viu_m, viu_n)
                ultimo_envio = t0
                estado = "livre" if saiu else "aguardando"
        elif estado == "aguardando" and not detectando:
            estado = "livre"

        time.sleep(max(0.0, dt - (time.monotonic() - t0)))


def sondar(cfg):
    mestre = Mestre(cfg["mestre_url"], sensor_urls=cfg.get("sensor_urls"))
    for nome, porta in cfg["portas"].items():
        try:
            bruto = mestre.ler_bruto(porta)
            if "data" not in bruto:
                print(f"porta {porta} ({nome}): sem valor (code {bruto.get('code')}; 530 = sem alvo no alcance) "
                      f"resposta={json.dumps(bruto)}")
                continue
            hx = bruto["data"]["value"]
            extra = ""
            if nome == "distancia" and cfg["distancia"].get("desloca_bits"):
                extra = f" distancia={int(hx, 16) >> cfg['distancia']['desloca_bits']} (use em vazio_raw/cheio_raw)"
            print(f"porta {porta} ({nome}): hex={hx} int={int(hx, 16)}{extra} resposta={json.dumps(bruto)}")
        except Exception as e:  # diagnostico: mostrar qualquer erro
            print(f"porta {porta} ({nome}): ERRO {e}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--config", default="config.json")
    ap.add_argument("--sondar", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    with open(args.config, encoding="utf-8") as f:
        cfg = json.load(f)

    api_url = os.getenv("GATEWAY_API_URL")
    api_key = os.getenv("GATEWAY_API_KEY")
    mestre_url = os.getenv("GATEWAY_MESTRE_URL")
    if api_url:
        cfg["api_url"] = api_url
    if api_key:
        cfg["api_key"] = api_key
    if mestre_url:
        cfg["mestre_url"] = mestre_url

    if args.sondar:
        sondar(cfg)
    else:
        try:
            executar(cfg, args.dry_run)
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()