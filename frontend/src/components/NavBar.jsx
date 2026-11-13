// src/components/NavBar.jsx
import React from 'react';
import { NavLink } from 'react-router-dom';

const ROTAS = [
  { caminho: '/', rotulo: 'Monitoramento' },
  { caminho: '/analise', rotulo: 'Análise e Histórico' },
  { caminho: '/ocorrencias', rotulo: 'Ocorrências', mostraAlertas: true },
  { caminho: '/usuarios', rotulo: 'Usuários' },
];

function NavBar({ qtdAlertas = 0 }) {
  return (
    <nav className="nav-abas mt-1" aria-label="Seções do dashboard">
      {ROTAS.map((rota) => (
        <NavLink
          key={rota.caminho}
          to={rota.caminho}
          end={rota.caminho === '/'}
          className={({ isActive }) => `tab-nav-item ${isActive ? 'active' : ''}`}
        >
          <span>{rota.rotulo}</span>
          {rota.mostraAlertas && qtdAlertas > 0 && (
            <span
              className="badge rounded-pill bg-danger-subtle text-danger fw-semibold"
              style={{ fontSize: '0.72rem', padding: '0.2em 0.55em' }}
            >
              {qtdAlertas}
            </span>
          )}
        </NavLink>
      ))}
    </nav>
  );
}

export default NavBar;
