// Permite mover e mudar o tamanho dos painéis:
//  - arrastar com um dedo (ou mouse) move o painel;
//  - botões − / + ou pinça com dois dedos diminuem/aumentam.
// Posição e tamanho ficam salvos neste aparelho (localStorage).

const CHAVE_POSICOES = "ra-esteira-posicoes";
const ESCALA_MIN = 0.7;
const ESCALA_MAX = 2.5;
const PASSO_ESCALA = 0.15;

const cardsArrastaveis = document.querySelectorAll(".card");
let camadaTopo = 1;

function lerPosicoes() {
  try {
    return JSON.parse(localStorage.getItem(CHAVE_POSICOES)) || {};
  } catch (e) {
    return {};
  }
}

function salvarPosicoes(posicoes) {
  try {
    localStorage.setItem(CHAVE_POSICOES, JSON.stringify(posicoes));
  } catch (e) {
    // sem armazenamento (aba anônima etc.): só não lembra a posição
  }
}

function limitar(valor, min, max) {
  return Math.min(Math.max(valor, min), Math.max(min, max));
}

function lerEscala(card) {
  return parseFloat(card.style.getPropertyValue("--escala")) || 1;
}

// Coloca o card em (x, y) em pixels, sem deixar sair da tela
function posicionarCard(card, x, y) {
  x = limitar(x, 0, window.innerWidth - card.offsetWidth);
  y = limitar(y, 0, window.innerHeight - card.offsetHeight);
  card.style.left = x + "px";
  card.style.top = y + "px";
  card.style.right = "auto";
  card.style.bottom = "auto";
}

// Muda o tamanho mantendo o centro do card no mesmo lugar
function mudarEscala(card, escala) {
  escala = limitar(escala, ESCALA_MIN, ESCALA_MAX);
  const antes = card.getBoundingClientRect();
  card.style.setProperty("--escala", escala.toFixed(2));
  const depois = card.getBoundingClientRect();
  posicionarCard(
    card,
    antes.left + antes.width / 2 - depois.width / 2,
    antes.top + antes.height / 2 - depois.height / 2
  );
}

function salvarCard(card) {
  const r = card.getBoundingClientRect();
  const posicoes = lerPosicoes();
  // posição em fração da tela para funcionar ao girar o celular
  posicoes[card.id] = {
    x: r.left / window.innerWidth,
    y: r.top / window.innerHeight,
    escala: lerEscala(card),
  };
  salvarPosicoes(posicoes);
}

// Reaplica posição e tamanho salvos (chamar quando os painéis aparecem,
// pois escondidos eles não têm tamanho)
function aplicarPosicoesSalvas() {
  const posicoes = lerPosicoes();
  cardsArrastaveis.forEach((card) => {
    const p = posicoes[card.id];
    if (!p) return;
    if (p.escala) card.style.setProperty("--escala", String(p.escala));
    posicionarCard(card, p.x * window.innerWidth, p.y * window.innerHeight);
  });
}

// Volta todos os cards para a posição e o tamanho iniciais
function organizarPaineis() {
  cardsArrastaveis.forEach((card) => card.removeAttribute("style"));
  salvarPosicoes({});
}

function distancia(a, b) {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function prepararCard(card) {
  const dedos = new Map(); // pointerId -> {x, y}
  let arraste = null;      // um dedo: move
  let pinca = null;        // dois dedos: muda o tamanho

  card.addEventListener("pointerdown", (e) => {
    if (e.target.closest("button")) return; // botões − / + não arrastam
    card.setPointerCapture(e.pointerId);
    dedos.set(e.pointerId, { x: e.clientX, y: e.clientY });
    card.style.zIndex = String(++camadaTopo); // card tocado fica por cima
    card.classList.add("arrastando");

    if (dedos.size === 1) {
      const r = card.getBoundingClientRect();
      arraste = { dx: e.clientX - r.left, dy: e.clientY - r.top };
    } else if (dedos.size === 2) {
      const [a, b] = [...dedos.values()];
      arraste = null;
      pinca = { distanciaInicial: distancia(a, b), escalaInicial: lerEscala(card) };
    }
  });

  card.addEventListener("pointermove", (e) => {
    if (!dedos.has(e.pointerId)) return;
    dedos.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (pinca && dedos.size >= 2) {
      const [a, b] = [...dedos.values()];
      mudarEscala(card, pinca.escalaInicial * (distancia(a, b) / pinca.distanciaInicial));
    } else if (arraste) {
      posicionarCard(card, e.clientX - arraste.dx, e.clientY - arraste.dy);
    }
  });

  function soltar(e) {
    if (!dedos.has(e.pointerId)) return;
    dedos.delete(e.pointerId);
    if (dedos.size < 2) pinca = null;
    if (dedos.size === 1) {
      // sobrou um dedo depois da pinça: continua arrastando com ele
      const [p] = [...dedos.values()];
      const r = card.getBoundingClientRect();
      arraste = { dx: p.x - r.left, dy: p.y - r.top };
    }
    if (dedos.size === 0) {
      arraste = null;
      card.classList.remove("arrastando");
      salvarCard(card);
    }
  }

  card.addEventListener("pointerup", soltar);
  card.addEventListener("pointercancel", soltar);

  // Botões − / +
  card.querySelectorAll("[data-escala]").forEach((botao) => {
    botao.addEventListener("click", () => {
      const direcao = Number(botao.dataset.escala);
      mudarEscala(card, lerEscala(card) + direcao * PASSO_ESCALA);
      salvarCard(card);
    });
  });
}

cardsArrastaveis.forEach(prepararCard);
document.getElementById("btn-organizar").addEventListener("click", organizarPaineis);

// Ao girar o celular, mantém os cards dentro da tela
window.addEventListener("resize", () => {
  if (!document.getElementById("paineis").hidden) aplicarPosicoesSalvas();
});
