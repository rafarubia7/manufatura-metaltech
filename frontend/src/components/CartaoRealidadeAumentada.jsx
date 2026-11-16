// src/components/CartaoRealidadeAumentada.jsx
// Cartão com o link e o QR code do app de Realidade Aumentada.
// Clicar no QR abre a imagem ampliada na tela, para ser lida pelo celular.
import React, { useEffect, useRef, useState } from 'react';
import qrcodeRa from '../assets/qrcode-ra.png';

const URL_RA =
  import.meta.env.VITE_RA_URL || 'https://rafarubia7.github.io/manufatura-metaltech/realidade-aumentada/';

function CartaoRealidadeAumentada() {
  const [ampliado, setAmpliado] = useState(false);
  const botaoFechar = useRef(null);

  // Com o QR ampliado: Esc fecha, a página de fundo não rola e o foco vai para o botão Fechar.
  useEffect(() => {
    if (!ampliado) return undefined;

    const aoTeclar = (evento) => {
      if (evento.key === 'Escape') setAmpliado(false);
    };
    const overflowAnterior = document.body.style.overflow;

    document.addEventListener('keydown', aoTeclar);
    document.body.style.overflow = 'hidden';
    botaoFechar.current?.focus();

    return () => {
      document.removeEventListener('keydown', aoTeclar);
      document.body.style.overflow = overflowAnterior;
    };
  }, [ampliado]);

  return (
    <>
      <div className="surface-card p-3 p-md-4">
        <div className="d-flex flex-column flex-sm-row align-items-center gap-3 gap-md-4">
          <button
            type="button"
            className="qr-miniatura"
            onClick={() => setAmpliado(true)}
            title="Clique para ampliar o QR code"
            aria-label="Ampliar o QR code do app de Realidade Aumentada"
          >
            <img src={qrcodeRa} alt="QR code do app de Realidade Aumentada" width="112" height="112" />
          </button>

          <div className="flex-grow-1 text-center text-sm-start">
            <span className="kpi-label mb-1">Realidade Aumentada</span>
            <p className="text-theme-secondary mb-3" style={{ fontSize: '0.88rem' }}>
              Aponte a câmera do celular para o QR code (clique nele para ampliar) e depois para a esteira: o app
              mostra a contagem de peças e o nível do reservatório sobre a imagem. Os dados do app são simulados,
              porque o celular não tem acesso à rede do laboratório.
            </p>
            <a className="btn btn-sm btn-outline-primary" href={URL_RA} target="_blank" rel="noopener noreferrer">
              Abrir o app de RA
            </a>
          </div>
        </div>
      </div>

      {ampliado && (
        <>
          <div
            className="modal d-block"
            role="dialog"
            aria-modal="true"
            aria-labelledby="titulo-qr-ra"
            onClick={() => setAmpliado(false)}
          >
            <div className="modal-dialog modal-dialog-centered" onClick={(evento) => evento.stopPropagation()}>
              <div className="modal-content">
                <div className="modal-header">
                  <h6 id="titulo-qr-ra" className="modal-title fw-semibold">
                    QR code do app de Realidade Aumentada
                  </h6>
                  <button
                    ref={botaoFechar}
                    type="button"
                    className="btn-close"
                    aria-label="Fechar"
                    onClick={() => setAmpliado(false)}
                  />
                </div>
                <div className="modal-body text-center">
                  <img src={qrcodeRa} alt="QR code do app de Realidade Aumentada" className="qr-ampliado" />
                  <p className="text-theme-secondary mt-3 mb-0 text-break" style={{ fontSize: '0.82rem' }}>
                    {URL_RA}
                  </p>
                </div>
              </div>
            </div>
          </div>
          <div className="modal-backdrop show" />
        </>
      )}
    </>
  );
}

export default CartaoRealidadeAumentada;
