// Lógica principal: eventos da RA e atualização da tela.

const el = {
  cena: document.getElementById("cena"),
  alvo: document.getElementById("alvo"),
  status: document.getElementById("status"),
  paineis: document.getElementById("paineis"),
  metalicas: document.getElementById("qtd-metalicas"),
  naoMetalicas: document.getElementById("qtd-nao-metalicas"),
  total: document.getElementById("qtd-total"),
  nivelValor: document.getElementById("nivel-valor"),
  nivelBarraContainer: document.getElementById("nivel-barra-container"),
  nivelBarra: document.getElementById("nivel-barra"),
  nivelAlerta: document.getElementById("nivel-alerta"),
};

const TEXTO_PROCURANDO = "Aponte a câmera para a imagem da esteira…";

// Esteira reconhecida: mostra os painéis
el.alvo.addEventListener("targetFound", () => {
  el.status.hidden = true;
  el.paineis.hidden = false;
});

// Esteira fora da câmera: esconde os painéis
el.alvo.addEventListener("targetLost", () => {
  el.paineis.hidden = true;
  el.status.textContent = TEXTO_PROCURANDO;
  el.status.hidden = false;
});
