// Lógica principal: eventos da RA e atualização da tela.

const el = {
  selo: document.getElementById("selo-teste"),
  cena: document.getElementById("cena"),
  alvo: document.getElementById("alvo"),
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
};

const TEXTO_PROCURANDO = "Aponte a câmera para a imagem da esteira…";

let temporizador = null;
let alvoVisivel = false;

// Deixa claro quando os dados são fictícios
el.selo.hidden = !MODO_TESTE;

// verde < 70%, amarelo 70–89%, vermelho >= 90%
function classificarNivel(nivel) {
  if (nivel >= 90) return { classe: "nivel-critico", texto: "Crítico: esvaziar o reservatório" };
  if (nivel >= 70) return { classe: "nivel-atencao", texto: "Atenção: reservatório enchendo" };
  return { classe: "nivel-normal", texto: "Normal" };
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
  const situacao = classificarNivel(nivel);
  el.nivelValor.textContent = nivel.toLocaleString("pt-BR", { maximumFractionDigits: 1 });
  el.nivelBarra.style.width = nivel + "%";
  el.nivelBarraContainer.setAttribute("aria-valuenow", String(nivel));
  el.nivelAlerta.textContent = situacao.texto;
  el.cardNivel.classList.remove("nivel-normal", "nivel-atencao", "nivel-critico");
  el.cardNivel.classList.add(situacao.classe);
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
}

function pararAtualizacao() {
  if (temporizador) {
    clearInterval(temporizador);
    temporizador = null;
  }
}

// Esteira reconhecida: mostra os painéis e começa a atualizar
el.alvo.addEventListener("targetFound", () => {
  alvoVisivel = true;
  el.status.hidden = true;
  el.paineis.hidden = false;
  iniciarAtualizacao();
});

// Esteira fora da câmera: esconde os painéis e para a atualização
el.alvo.addEventListener("targetLost", () => {
  alvoVisivel = false;
  pararAtualizacao();
  el.paineis.hidden = true;
  mostrarStatus(TEXTO_PROCURANDO, false);
});

// A câmera só abre em HTTPS ou localhost (não funciona via file:// ou http na rede)
if (!window.isSecureContext) {
  mostrarStatus("A câmera só abre em HTTPS ou localhost. Use o link do GitHub Pages.", true);
}

// Falha ao iniciar a RA (câmera negada, sem câmera, sem HTTPS...)
el.cena.addEventListener("arError", () => {
  alvoVisivel = false;
  pararAtualizacao();
  el.paineis.hidden = true;
  mostrarStatus("Não foi possível acessar a câmera. Verifique a permissão e se o site está em HTTPS.", true);
});
