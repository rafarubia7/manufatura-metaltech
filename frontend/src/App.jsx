// src/App.jsx
import React, { useState, useEffect } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import NavBar from './components/NavBar';
import PainelAoVivo from './components/PainelAoVivo';
import GraficosHistorico from './components/GraficosHistorico';
import TabelaManutencoes from './components/TabelaManutencoes';
import GestaoUsuarios from './components/GestaoUsuarios';
import { buscarAlertas, buscarEstatisticas } from './services/api';

function App() {
  const [apiOnline, setApiOnline] = useState(false);
  const [qtdAlertas, setQtdAlertas] = useState(0);
  const [tema, setTema] = useState(() => localStorage.getItem('metaltech-tema') || 'light');

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', tema);
    document.documentElement.setAttribute('data-bs-theme', tema); // tema escuro também nos componentes do Bootstrap
    localStorage.setItem('metaltech-tema', tema);
  }, [tema]);


  const alternarTema = () => {
    setTema((prev) => (prev === 'light' ? 'dark' : 'light'));
  };

  const verificarStatusSistema = async () => {
    try {
      await buscarEstatisticas();
      setApiOnline(true);

      const alertas = await buscarAlertas();
      const ativos = Array.isArray(alertas)
        ? alertas.filter((a) => !a.resolvido).length
        : 0;
      setQtdAlertas(ativos);
    } catch {
      setApiOnline(false);
    }
  };

  useEffect(() => {
    verificarStatusSistema();
    const intervalo = setInterval(verificarStatusSistema, 5000);
    return () => clearInterval(intervalo);
  }, []);

  return (
    <div className="min-vh-100 d-flex flex-column">
      {/* Cabeçalho Clean */}
      <header className="header-surface">
        <div className="container pt-3">
          {/* Linha superior: Marca + Status + Tema */}
          <div className="d-flex justify-content-between align-items-center pb-2">
            <div className="d-flex align-items-baseline gap-3">
              <span className="fw-bold fs-5 text-theme-primary" style={{ letterSpacing: '-0.03em' }}>
                MetalTech
              </span>
              <span className="text-theme-secondary d-none d-sm-inline" style={{ fontSize: '0.82rem' }}>
                Esteira de triagem
              </span>
            </div>

            <div className="d-flex align-items-center gap-3">
              <div className="d-flex align-items-center gap-2">
                <span className={`live-indicator ${apiOnline ? 'online' : 'offline'}`}></span>
                <span className="text-theme-secondary" style={{ fontSize: '0.8rem', fontWeight: 500 }}>
                  {apiOnline ? 'Conectado' : 'Sem conexão'}
                </span>
              </div>

              <button
                type="button"
                className="theme-toggle-btn"
                onClick={alternarTema}
                title={tema === 'light' ? 'Mudar para tema escuro' : 'Mudar para tema claro'}
              >
                {tema === 'light' ? (
                  <>
                    <svg width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z" />
                    </svg>
                    <span className="d-none d-md-inline">Escuro</span>
                  </>
                ) : (
                  <>
                    <svg width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                      <circle cx="12" cy="12" r="5" />
                      <path strokeLinecap="round" d="M12 1v2m0 18v2M4.22 4.22l1.42 1.42m12.72 12.72l1.42 1.42M1 12h2m18 0h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
                    </svg>
                    <span className="d-none d-md-inline">Claro</span>
                  </>
                )}
              </button>
            </div>
          </div>

          {/* Linha inferior: navegação por rotas */}
          <NavBar qtdAlertas={qtdAlertas} />
        </div>
      </header>

      {/* Conteúdo */}
      <main className="container py-4 flex-grow-1">
        <Routes>
          <Route path="/" element={<PainelAoVivo />} />
          <Route path="/analise" element={<GraficosHistorico tema={tema} />} />
          <Route path="/ocorrencias" element={<TabelaManutencoes aoAlterarAlertas={verificarStatusSistema} />} />
          <Route path="/usuarios" element={<GestaoUsuarios />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;