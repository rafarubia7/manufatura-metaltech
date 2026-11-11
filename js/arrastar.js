// Permite mover os painéis arrastando com o dedo (ou mouse).
// A posição escolhida fica salva neste aparelho (localStorage).

const CHAVE_POSICOES = "ra-esteira-posicoes";
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

// Coloca o card em (x, y) em pixels, sem deixar sair da tela
function posicionarCard(card, x, y) {
  x = limitar(x, 0, window.innerWidth - card.offsetWidth);
  y = limitar(y, 0, window.innerHeight - card.offsetHeight);
  card.style.left = x + "px";
  card.style.top = y + "px";
  card.style.right = "auto";
  card.style.bottom = "auto";
  card.style.transform = "none";
  return { x, y };
}

// Reaplica as posições salvas (chamar quando os painéis aparecem,
// pois escondidos eles não têm tamanho)
function aplicarPosicoesSalvas() {
  const posicoes = lerPosicoes();
  cardsArrastaveis.forEach((card) => {
    const p = posicoes[card.id];
    if (p) {
      // salvo em fração da tela para funcionar ao girar o celular
      posicionarCard(card, p.x * window.innerWidth, p.y * window.innerHeight);
    }
  });
}

// Volta todos os cards para a posição inicial
function organizarPaineis() {
  cardsArrastaveis.forEach((card) => card.removeAttribute("style"));
  salvarPosicoes({});
}

function tornarArrastavel(card) {
  let arraste = null;

  card.addEventListener("pointerdown", (e) => {
    const r = card.getBoundingClientRect();
    arraste = { id: e.pointerId, dx: e.clientX - r.left, dy: e.clientY - r.top };
    card.setPointerCapture(e.pointerId);
    card.classList.add("arrastando");
    card.style.zIndex = String(++camadaTopo); // card arrastado fica por cima
  });

  card.addEventListener("pointermove", (e) => {
    if (!arraste || e.pointerId !== arraste.id) return;
    posicionarCard(card, e.clientX - arraste.dx, e.clientY - arraste.dy);
  });

  function soltar(e) {
    if (!arraste || e.pointerId !== arraste.id) return;
    arraste = null;
    card.classList.remove("arrastando");
    const r = card.getBoundingClientRect();
    const posicoes = lerPosicoes();
    posicoes[card.id] = { x: r.left / window.innerWidth, y: r.top / window.innerHeight };
    salvarPosicoes(posicoes);
  }

  card.addEventListener("pointerup", soltar);
  card.addEventListener("pointercancel", soltar);
}

cardsArrastaveis.forEach(tornarArrastavel);
document.getElementById("btn-organizar").addEventListener("click", organizarPaineis);

// Ao girar o celular, mantém os cards dentro da tela
window.addEventListener("resize", () => {
  if (!document.getElementById("paineis").hidden) aplicarPosicoesSalvas();
});
