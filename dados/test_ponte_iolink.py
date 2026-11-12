import pytest

from script_ponte_iolink import (
    classificar_peca,
    construir_payload,
    hex_para_decimal,
    status_reservatorio,
)


def test_hex_sem_sinal():
    assert hex_para_decimal("0161") == 353
    assert hex_para_decimal("7FE0") == 32736


def test_hex_com_sinal():
    assert hex_para_decimal("FF80", bits=16, signed=True) == -128


def test_hex_invalido():
    with pytest.raises(ValueError):
        hex_para_decimal("xyz")


@pytest.mark.parametrize("metal,nao_metal,esperado", [
    (True, False, "METALICA"),
    (False, True, "NAO_METALICA"),
    (True, True, "AMBAS"),
    (False, False, "DESCONHECIDA"),
])
def test_classificar_peca(metal, nao_metal, esperado):
    assert classificar_peca(metal, nao_metal) == esperado


@pytest.mark.parametrize("percentual,esperado", [
    (10, "ALERTA_BAIXO"), (20, "NORMAL"), (80, "NORMAL"), (80.1, "ALERTA_ALTO"), (95, "CRITICO"),
])
def test_status_reservatorio(percentual, esperado):
    assert status_reservatorio(percentual) == esperado


def test_payload_deriva_tipo_dos_sensores():
    bruto = {"distancia_raw": "0008", "indutivo_raw": "0F00", "capacitivo_raw": "0000", "nivel_raw": 55}
    payload = construir_payload(bruto, dispositivo_id="ESP32-001")
    assert payload["peca"]["tipo"] == "METALICA"
    assert payload["sensorIndutivo"]["detectouMetal"] is True
    assert payload["nivelReservatorio"]["status"] == "NORMAL"
