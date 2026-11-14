// Lógica principal: eventos da RA e atualização da tela.

const el = {
  selo: document.getElementById("selo-teste"),
  cena: document.getElementById("cena"),
  alvos: document.querySelectorAll("[mindar-image-target]"),
  status: document.getElementById("status"),
  paineis: document.getElementById("paineis"),
  metalicas: document.getElementById("qtd-metalicas"),
  naoMetalicas: document.getElementById("qtd-nao-metalicas"),
  total: document.getElementById("qtd-total"),
  cardNivel: document.getElementById("card-nivel"),
  nivelValor: document.getElementById("nivel-valor"),
  nivelBarraContainer: document.getElementById("nivel-barra-container"),
  nivelBarra: document.getElementById("nivel-barra"),
  nivelAlerta: document.getElementById("nivel-alerta"),
  faixaEnchimento: document.getElementById("faixa-enchimento"),
  atualizadoHa: document.getElementById("atualizado-ha"),
};

const TEXTO_PROCURANDO = "Aponte a câmera para a esteira…";

let temporizador = null;
let alvoVisivel = false;
let alvosVisiveis = 0;       // quantos alvos (fotos da esteira) estão na câmera
let temporizadorPerda = null; // espera antes de esconder os painéis
let temporizadorRelogio = null; // atualiza o "Atualizado há X s" a cada 1 s
let ultimaLeitura = null;       // instante (ms) da última leitura recebida

// Deixa claro quando os dados são fictícios
el.selo.hidden = !MODO_TESTE;

// Cor e texto conforme o status recebido (limiares em js/config.js)
const SITUACOES = {
  CRITICO: { classe: "nivel-critico", texto: "Crítico: esvaziar o reservatório" },
  ALERTA_ALTO: { classe: "nivel-atencao", texto: "Atenção: perto da capacidade" },
  NORMAL: { classe: "nivel-normal", texto: "Normal" },
  ALERTA_BAIXO: { classe: "nivel-normal", texto: "Normal (nível baixo)" },
};

function classificarNivel(status) {
  return SITUACOES[status] || SITUACOES.NORMAL;
}

// "Atualizado agora" (< 2 s) ou "Atualizado há X s"
function mostrarTempoDesdeLeitura() {
  if (ultimaLeitura === null) return;
  const segundos = Math.max(0, Math.floor((Date.now() - ultimaLeitura) / 1000));
  el.atualizadoHa.textContent = segundos < 2 ? "Atualizado agora" : "Atualizado há " + segundos + " s";
}

function iniciarRelogio() {
  pararRelogio();
  mostrarTempoDesdeLeitura();
  temporizadorRelogio = setInterval(mostrarTempoDesdeLeitura, 1000);
}

function pararRelogio() {
  if (temporizadorRelogio) {
    clearInterval(temporizadorRelogio);
    temporizadorRelogio = null;
  }
}

function mostrarStatus(texto, ehErro) {
  el.status.textContent = texto;
  el.status.classList.toggle("erro", Boolean(ehErro));
  el.status.hidden = false;
}

function mostrarDados(dados) {
  const total = dados.metalicas + dados.naoMetalicas;
  el.metalicas.textContent = dados.metalicas.toLocaleString("pt-BR");
  el.naoMetalicas.textContent = dados.naoMetalicas.toLocaleString("pt-BR");
  el.total.textContent = total.toLocaleString("pt-BR");

  const nivel = Math.min(100, Math.max(0, dados.nivelReservatorio));
  const situacao = classificarNivel(dados.status);
  el.nivelValor.textContent = nivel.toLocaleString("pt-BR", { maximumFractionDigits: 1 });
  el.nivelBarra.style.width = nivel + "%";
  el.nivelBarraContainer.setAttribute("aria-valuenow", String(nivel));
  el.nivelAlerta.textContent = situacao.texto;
  el.cardNivel.classList.remove("nivel-normal", "nivel-atencao", "nivel-critico");
  el.cardNivel.classList.add(situacao.classe);

  // Faixa "fora do padrão": só enquanto a leitura indicar enchimento rápido
  const enchimentoRapido = dados.exibirAlerta === true &&
    Array.isArray(dados.motivos) && dados.motivos.includes("ENCHIMENTO_RAPIDO");
  el.faixaEnchimento.hidden = !enchimentoRapido;

  const instante = Date.parse(dados.ultimaLeitura);
  ultimaLeitura = Number.isNaN(instante) ? Date.now() : instante;
  mostrarTempoDesdeLeitura();
}

async function atualizarDados() {
  try {
    const dados = await obterDados();
    if (!alvoVisivel) return; // resposta chegou depois de perder o alvo
    mostrarDados(dados);
    el.status.hidden = true; // some com erro anterior, se houver
  } catch (erro) {
    if (!alvoVisivel) return;
    mostrarStatus(erro.message, true);
  }
}

// setInterval só roda enquanto o alvo está visível
function iniciarAtualizacao() {
  pararAtualizacao();
  atualizarDados();
  temporizador = setInterval(atualizarDados, INTERVALO_MS);
  iniciarRelogio();
}

function pararAtualizacao() {
  if (temporizador) {
    clearInterval(temporizador);
    temporizador = null;
  }
  pararRelogio();
}

// Esteira reconhecida: mostra os painéis e começa a atualizar
function aoEncontrarEsteira() {
  alvosVisiveis++;
  clearTimeout(temporizadorPerda);
  if (alvoVisivel) return; // já estava na tela (perda curta ou outro alvo)
  alvoVisivel = true;
  el.status.hidden = true;
  el.paineis.hidden = false;
  aplicarPosicoesSalvas(); // posições escolhidas pelo usuário (js/arrastar.js)
  iniciarAtualizacao();
}

// Esteira fora da câmera: espera um pouco e, se não voltar,
// esconde os painéis e para a atualização
function aoPerderEsteira() {
  alvosVisiveis = Math.max(0, alvosVisiveis - 1);
  if (alvosVisiveis > 0) return; // outro alvo da esteira ainda está visível
  clearTimeout(temporizadorPerda);
  temporizadorPerda = setTimeout(() => {
    alvoVisivel = false;
    pararAtualizacao();
    el.paineis.hidden = true;
    mostrarStatus(TEXTO_PROCURANDO, false);
  }, TOLERANCIA_PERDA_MS);
}

el.alvos.forEach((alvo) => {
  alvo.addEventListener("targetFound", aoEncontrarEsteira);
  alvo.addEventListener("targetLost", aoPerderEsteira);
});

// A câmera só abre em HTTPS ou localhost (não funciona via file:// ou http na rede)
if (!window.isSecureContext) {
  mostrarStatus("A câmera só abre em HTTPS ou localhost. Use o link do GitHub Pages.", true);
}

// Falha ao iniciar a RA (câmera negada, sem câmera, sem HTTPS...)
el.cena.addEventListener("arError", () => {
  clearTimeout(temporizadorPerda);
  alvoVisivel = false;
  pararAtualizacao();
  el.paineis.hidden = true;
  mostrarStatus("Não foi possível acessar a câmera. Verifique a permissão e se o site está em HTTPS.", true);
});
