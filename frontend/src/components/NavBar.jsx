// src/components/NavBar.jsx
import React from 'react';

function NavBar({ setTelaAtiva, telaAtiva, pendingAlerts = 0 }) {
  const tabs = [
    { key: 'aoVivo', label: 'Monitoramento ao Vivo' },
    { key: 'relatorios', label: 'Relatórios de Produção' },
    { key: 'historico', label: 'Histórico de Ocorrências' },
  ];

  return (
    <nav className="navbar navbar-expand-lg">
      <div className="container-fluid">
        <div className="navbar-nav">
          {tabs.map((tab) => {
            const isActive = telaAtiva === tab.key;
            const hasPending = tab.key === 'historico' && pendingAlerts > 0;

            return (
              <button
                key={tab.key}
                className={`nav-link btn btn-link ${isActive ? 'active' : ''}`}
                onClick={() => setTelaAtiva(tab.key)}
                type="button"
              >
                <span>{tab.label}</span>
                {hasPending && <span className="alert-count">{pendingAlerts}</span>}
              </button>
            );
          })}
        </div>
      </div>
    </nav>
  );
}

export default NavBar;