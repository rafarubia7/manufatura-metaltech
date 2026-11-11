// =============================================================
// ATENÇÃO: DADOS SIMULADOS (FICTÍCIOS) — APENAS PARA TESTE!
// -------------------------------------------------------------
// Enquanto MODO_TESTE = true (js/config.js), os números abaixo
// são inventados aleatoriamente e NÃO vêm da esteira real.
//
// Para usar a API real:
//   1. Coloque o endereço em API_URL (js/config.js)
//   2. Mude MODO_TESTE para false
// A função obterDados() deve sempre devolver este formato:
//   { metalicas: 42, naoMetalicas: 17, nivelReservatorio: 78.5 }
// =============================================================

// Estado da simulação (só usado no modo teste)
const simulacao = {
  metalicas: 0,
  naoMetalicas: 0,
  nivelReservatorio: 20,
};

function numeroAleatorio(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// Gera dados fictícios: as contagens sobem um pouco a cada chamada
// e o reservatório vai enchendo até ser "esvaziado".
function gerarDadosSimulados() {
  simulacao.metalicas += numeroAleatorio(0, 4);
  simulacao.naoMetalicas += numeroAleatorio(0, 3);
  simulacao.nivelReservatorio += Math.random() * 9;

  if (simulacao.nivelReservatorio > 100) {
    simulacao.nivelReservatorio = numeroAleatorio(5, 25); // esvaziou
  }

  return {
    metalicas: simulacao.metalicas,
    naoMetalicas: simulacao.naoMetalicas,
    nivelReservatorio: Math.round(simulacao.nivelReservatorio * 10) / 10,
  };
}

// Busca os dados da esteira.
// Retorna uma Promise com { metalicas, naoMetalicas, nivelReservatorio }.
// Em caso de falha na API, lança Error("Sem conexão com a API").
async function obterDados() {
  if (MODO_TESTE) {
    return gerarDadosSimulados();
  }

  try {
    if (!API_URL) {
      throw new Error("API_URL não configurada");
    }

    const resposta = await fetch(API_URL, { cache: "no-store" });
    if (!resposta.ok) {
      throw new Error("HTTP " + resposta.status);
    }

    const json = await resposta.json();
    return {
      metalicas: Number(json.metalicas) || 0,
      naoMetalicas: Number(json.naoMetalicas) || 0,
      nivelReservatorio: Number(json.nivelReservatorio) || 0,
    };
  } catch (erro) {
    console.error("Erro ao buscar dados da API:", erro);
    throw new Error("Sem conexão com a API");
  }
}
