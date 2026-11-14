// =============================================================
// ATENÇÃO: DADOS SIMULADOS (FICTÍCIOS) — APENAS PARA DEMONSTRAÇÃO!
// -------------------------------------------------------------
// Os números abaixo são gerados aleatoriamente e NÃO vêm da
// esteira real. obterDados() sempre devolve este formato:
//   {
//     metalicas: 42,
//     naoMetalicas: 17,
//     nivelReservatorio: 78.5,
//     status: "ALERTA_BAIXO" | "NORMAL" | "ALERTA_ALTO" | "CRITICO",
//     exibirAlerta: false,
//     motivos: [],                 // ou ["ENCHIMENTO_RAPIDO"]
//     ultimaLeitura: "2026-11-13T12:00:00.000Z"
//   }
// Os limiares ficam em js/config.js.
// =============================================================

const NIVEL_ESVAZIAR = 97;   // acima disso o reservatório "esvazia"
const NIVEL_REINICIO = 20;   // nível aproximado depois de esvaziar

function numeroAleatorio(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function decimalAleatorio(min, max) {
  return Math.random() * (max - min) + min;
}

function arredondar(valor) {
  return Math.round(valor * 10) / 10;
}

// Estado da simulação, mantido entre as chamadas
const simulacao = {
  metalicas: 0,
  naoMetalicas: 0,
  nivelReservatorio: NIVEL_REINICIO,
  atualizacoesAteSalto: numeroAleatorio(8, 10),
};

function calcularStatus(nivel) {
  if (nivel >= LIMITE_CRITICO) return "CRITICO";
  if (nivel >= LIMITE_ALERTA_ALTO) return "ALERTA_ALTO";
  if (nivel < LIMITE_ALERTA_BAIXO) return "ALERTA_BAIXO";
  return "NORMAL";
}

// Gera a próxima leitura fictícia: contagens só crescem, o nível sobe
// devagar e, a cada 8 a 10 atualizações, dá um salto acima do padrão.
function gerarDadosSimulados() {
  simulacao.metalicas += numeroAleatorio(0, 4);
  simulacao.naoMetalicas += numeroAleatorio(0, 3);

  const nivelAnterior = simulacao.nivelReservatorio;
  let nivel;

  if (nivelAnterior > NIVEL_ESVAZIAR) {
    // esvaziou: recomeça perto de 20%
    nivel = NIVEL_REINICIO + decimalAleatorio(-2, 2);
  } else {
    simulacao.atualizacoesAteSalto--;
    if (simulacao.atualizacoesAteSalto <= 0) {
      nivel = nivelAnterior + SALTO_ENCHIMENTO_RAPIDO + decimalAleatorio(1, 3);
      simulacao.atualizacoesAteSalto = numeroAleatorio(8, 10);
    } else {
      nivel = nivelAnterior + decimalAleatorio(0.5, 2);
    }
  }

  nivel = Math.min(100, arredondar(nivel));
  simulacao.nivelReservatorio = nivel;

  const enchimentoRapido = nivel - nivelAnterior > SALTO_ENCHIMENTO_RAPIDO;
  const motivos = enchimentoRapido ? ["ENCHIMENTO_RAPIDO"] : [];

  return {
    metalicas: simulacao.metalicas,
    naoMetalicas: simulacao.naoMetalicas,
    nivelReservatorio: nivel,
    status: calcularStatus(nivel),
    exibirAlerta: motivos.length > 0,
    motivos: motivos,
    ultimaLeitura: new Date().toISOString(),
  };
}

// Retorna uma Promise com a leitura simulada (formato descrito acima).
async function obterDados() {
  return gerarDadosSimulados();
}
