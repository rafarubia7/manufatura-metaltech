// src/components/NavBar.jsx
import React from 'react';

function NavBar({ setTelaAtiva, telaAtiva }) {
  return (
    <nav className="navbar navbar-expand-lg navbar-dark bg-dark">
      <div className="container-fluid">
        <span className="navbar-brand font-weight-bold">MetalTech</span>
        <div className="navbar-nav">
          <button 
            className={`nav-link btn btn-link ${telaAtiva === 'aoVivo' ? 'active' : ''}`}
            onClick={() => setTelaAtiva('aoVivo')}
          >
            Monitoramento ao Vivo
          </button>
          <button 
            className={`nav-link btn btn-link ${telaAtiva === 'relatorios' ? 'active' : ''}`}
            onClick={() => setTelaAtiva('relatorios')}
          >
            Relatórios de Produção
          </button>
          <button 
            className={`nav-link btn btn-link ${telaAtiva === 'historico' ? 'active' : ''}`}
            onClick={() => setTelaAtiva('historico')}
          >
            Histórico de Ocorrências
          </button>
        </div>
      </div>
    </nav>
  );
}

export default NavBar;