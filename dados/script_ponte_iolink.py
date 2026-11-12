"""
Script Ponte: busca dados brutos do IO-Link local em HEX, converte para Decimal,
normaliza para o payload esperado pela API Spring Boot e faz o POST.

Uso:
    python script_ponte_iolink.py
    python script_ponte_iolink.py --iolink-url http://10.0.0.25:8081/sensores --api-url http://localhost:8080/api/v1/leituras
"""

from __future__ import annotations

import argparse
import json
import os
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests


DEFAULT_IO_LINK_URL = "http://localhost:8081/sensores"
DEFAULT_API_URL = "http://localhost:8080/api/v1/leituras"


def agora_iso_utc() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def safe_float(valor: Any, *, default: float | None = None) -> float | None:
    if valor is None:
        return default
    if isinstance(valor, (int, float)):
        return float(valor)
    if isinstance(valor, str):
        texto = valor.strip().replace(" ", "")
        if texto in {"", "null", "None", "NaN", "nan"}:
            return default
        try:
            return float(texto)
        except ValueError:
            return default
    return default


def hex_para_decimal(raw_value: Any, *, bits: int = 16, signed: bool = False) -> float | None:
    """Converte um valor hexadecimal em decimal, com suporte para valores com sinal.

    Exemplos:
      hex_para_decimal("0161") -> 353
      hex_para_decimal("7FE0") -> 32768
      hex_para_decimal("FF80", bits=16, signed=True) -> -128
    """
    if raw_value is None:
        return None

    if isinstance(raw_value, (int, float)) and not isinstance(raw_value, bool):
        return float(raw_value)

    texto = str(raw_value).strip().lower()
    texto = texto.replace("0x", "").replace(" ", "")
    if not texto:
        return None
    if not re.fullmatch(r"[0-9a-f]+", texto):
        raise ValueError(f"Valor hexadecimal inválido: {raw_value!r}")

    inteiro = int(texto, 16)
    if signed:
        limite = 1 << bits
        if inteiro & (1 << (bits - 1)):
            inteiro -= limite
    return float(inteiro)


def buscar_campo(dados: dict[str, Any], chaves: list[str]) -> Any:
    for chave in chaves:
        if chave in dados:
            return dados[chave]
    return None


def extrair_dados_iolink(dados_brutos: dict[str, Any]) -> dict[str, Any]:
    """Ajuste este mapeamento de acordo com a estrutura real do seu IO-Link local.

    O script aceita chaves em diferentes formatos, como:
    - {'distancia_cm': '0161'}
    - {'distancia': {'raw': '0161'}}
    - {'sensor': {'distancia_cm': '0161'}}
    """
    distancia_raw = (
        buscar_campo(dados_brutos, ["distancia_cm", "distancia", "sensor_distancia", "distance_cm"])
    )
    if isinstance(distancia_raw, dict):
        distancia_raw = buscar_campo(distancia_raw, ["raw", "valor", "value", "hex", "hexadecimal"])

    indutivo_raw = buscar_campo(dados_brutos, ["indutivo_intensidade", "sensor_indutivo", "indutivo"])
    if isinstance(indutivo_raw, dict):
        indutivo_raw = buscar_campo(indutivo_raw, ["raw", "valor", "value", "hex", "hexadecimal"])

    capacitivo_raw = buscar_campo(dados_brutos, ["capacitivo_intensidade", "sensor_capacitivo", "capacitivo"])
    if isinstance(capacitivo_raw, dict):
        capacitivo_raw = buscar_campo(capacitivo_raw, ["raw", "valor", "value", "hex", "hexadecimal"])

    nivel_raw = buscar_campo(dados_brutos, ["nivel_reservatorio_percentual", "nivel", "nivel_reservatorio"])
    if isinstance(nivel_raw, dict):
        nivel_raw = buscar_campo(nivel_raw, ["raw", "valor", "value", "hex", "hexadecimal", "percentual"])

    # Se o payload do IO-Link vier em formato de lista, tenta extrair os campos em ordem.
    if isinstance(dados_brutos, list) and len(dados_brutos) >= 4:
        distancia_raw = dados_brutos[0]
        indutivo_raw = dados_brutos[1]
        capacitivo_raw = dados_brutos[2]
        nivel_raw = dados_brutos[3]

    return {
        "distancia_raw": distancia_raw,
        "indutivo_raw": indutivo_raw,
        "capacitivo_raw": capacitivo_raw,
        "nivel_raw": nivel_raw,
    }


def construir_payload(dados_iolink: dict[str, Any], *, dispositivo_id: str, versao_firmware: str = "1.0.0") -> dict[str, Any]:
    distancia = hex_para_decimal(dados_iolink["distancia_raw"], bits=16, signed=False)
    indutivo = hex_para_decimal(dados_iolink["indutivo_raw"], bits=16, signed=False)
    capacitivo = hex_para_decimal(dados_iolink["capacitivo_raw"], bits=16, signed=False)
    nivel = safe_float(dados_iolink["nivel_raw"], default=0.0)

    if nivel is None:
        nivel = 0.0

    instante = agora_iso_utc()
    sem_sinal = (indutivo is not None and indutivo < 0.1) and (capacitivo is not None and capacitivo < 0.1)
    status_sensor = "SEM_SINAL" if sem_sinal else "OK"

    # Os valores de intensidade e distância podem precisar de escala específica do equipamento.
    # Caso a sua instalação use conversão linear, ajuste aqui o fator e o offset.
    distancia_cm = safe_float(distancia, default=0.0)
    indutivo_intensidade = safe_float(indutivo, default=0.0)
    capacitivo_intensidade = safe_float(capacitivo, default=0.0)

    # Classificação genérica derivada do nível de reservatório.
    tipo_peca = "DESCONHECIDA"
    if nivel is not None:
        tipo_peca = "DESCONHECIDA"

    payload = {
        "timestamp": instante,
        "sensorDistancia": {
            "valor": float(distancia_cm),
            "unidade": "cm",
            "status": "OK",
        },
        "sensorIndutivo": {
            "detectouMetal": bool(indutivo_intensidade > 0.1),
            "intensidade": float(indutivo_intensidade),
            "status": status_sensor,
        },
        "sensorCapacitivo": {
            "detectouNaoMetal": bool(capacitivo_intensidade > 0.1),
            "intensidade": float(capacitivo_intensidade),
            "status": status_sensor,
        },
        "peca": {
            "id": f"peca-{int(time.time() * 1000)}",
            "tipo": tipo_peca,
            "timestampDetectado": instante,
        },
        "nivelReservatorio": {
            "nivelAtual": float(nivel),
            "capacidadeMaxima": 100.0,
            "percentual": float(nivel),
            "status": "NORMAL",
        },
        "dispositivoId": dispositivo_id,
        "versaoFirmware": versao_firmware,
    }
    return payload


def consultar_iolink(url: str, timeout: int = 20) -> dict[str, Any]:
    resposta = requests.get(url, timeout=timeout)
    resposta.raise_for_status()
    try:
        return resposta.json()
    except ValueError as erro:
        raise ValueError(f"Resposta do IO-Link não é JSON válida: {resposta.text[:500]}") from erro


def cabecalhos_api() -> dict[str, str]:
    chave = os.environ.get("METALTECH_API_KEY")
    return {"X-API-Key": chave} if chave else {}


def postar_api(url: str, payload: dict[str, Any], timeout: int = 30) -> requests.Response:
    resposta = requests.post(url, json=payload, headers=cabecalhos_api(), timeout=timeout)
    if resposta.status_code not in {200, 201}:
        raise RuntimeError(f"Falha ao enviar para a API: HTTP {resposta.status_code} | {resposta.text[:500]}")
    return resposta


def principal() -> None:
    analise = argparse.ArgumentParser(description="Ponte entre IO-Link em HEX e API Spring Boot")
    analise.add_argument("--iolink-url", default=DEFAULT_IO_LINK_URL, help="Endpoint do IO-Link local (GET)")
    analise.add_argument("--api-url", default=DEFAULT_API_URL, help="Endpoint do Spring Boot (POST)")
    analise.add_argument("--dispositivo-id", default="ESP32-001", help="Identificador do dispositivo")
    analise.add_argument("--versao-firmware", default="1.0.0", help="Versão do firmware")
    analise.add_argument("--intervalo-segundos", type=float, default=5.0, help="Intervalo entre ciclos de leitura")
    analise.add_argument("--max-ciclos", type=int, default=1, help="Número de ciclos; 0 = infinito")
    argumentos = analise.parse_args()

    ciclo = 0
    while True:
        ciclo += 1
        try:
            bruto = consultar_iolink(argumentos.iolink_url)
            dados = extrair_dados_iolink(bruto)
            payload = construir_payload(dados, dispositivo_id=argumentos.dispositivo_id, versao_firmware=argumentos.versao_firmware)
            resposta = postar_api(argumentos.api_url, payload)
            print(f"[{datetime.now().isoformat(timespec='seconds')}] POST OK -> status={resposta.status_code}, payload={json.dumps(payload, ensure_ascii=False)[:350]}")
        except Exception as erro:
            print(f"[{datetime.now().isoformat(timespec='seconds')}] ERRO: {erro}")

        if argumentos.max_ciclos and ciclo >= argumentos.max_ciclos:
            break
        if argumentos.intervalo_segundos > 0:
            time.sleep(argumentos.intervalo_segundos)


if __name__ == "__main__":
    principal()
