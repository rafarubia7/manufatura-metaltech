// src/components/PainelAoVivo.jsx
import React, { useState, useEffect } from 'react';

function PainelAoVivo() {
  // Dados de teste recomendados no roteiro
  const [dadosEsteira, setDadosEsteira] = useState({
    metalicas: 15,
    plasticas: 8,
    nivel: 45
  });
  const [erroConexao, setErroConexao] = useState(false);

  // Simula a busca de dados a cada 3 segundos
  useEffect(() => {
    const intervalo = setInterval(() => {
      // Quando tiver a API real, substitua este bloco pelo fetch()
      try {
        setErroConexao(false);
        // Simulando a chegada de novas peças para testar a reatividade
        setDadosEsteira(prev => ({
          metalicas: prev.metalicas + Math.floor(Math.random() * 2),
          plasticas: prev.plasticas + Math.floor(Math.random() * 2),
          nivel: prev.nivel >= 100 ? 0 : prev.nivel + 5
        }));
      } catch (error) {
        setErroConexao(true);
      }
    }, 3000);

    return () => clearInterval(intervalo);
  }, []);

  // Lógica de cores do reservatório
  const corBarra = dadosEsteira.nivel > 85 ? 'bg-danger' : 'bg-success';
  const alertaReservatorio = dadosEsteira.nivel > 85;

  return (
    <div>
      {/* Tratamento de falha amigável */}
      {erroConexao && (
        <div className="alert alert-warning text-center">
          Tentando reconectar à esteira...
        </div>
      )}

      {alertaReservatorio && (
        <div className="alert alert-danger text-center fw-bold animate-pulse">
          Atenção: Reservatório Quase Cheio!
        </div>
      )}

      <div className="row">
        {/* Cartão 1 - Peças Metálicas */}
        <div className="col-md-4">
          <div className="card text-center shadow-sm">
            <div className="card-body">
              <h5 className="card-title text-secondary">Peças Metálicas</h5>
              <h1 className="display-4 text-primary">{dadosEsteira.metalicas}</h1>
            </div>
          </div>
        </div>

        {/* Cartão 2 - Peças Não Metálicas */}
        <div className="col-md-4">
          <div className="card text-center shadow-sm">
            <div className="card-body">
              <h5 className="card-title text-secondary">Peças Plásticas</h5>
              <h1 className="display-4 text-secondary">{dadosEsteira.plasticas}</h1>
            </div>
          </div>
        </div>

        {/* Cartão 3 - Nível do Reservatório */}
        <div className="col-md-4">
          <div className="card text-center shadow-sm">
            <div className="card-body">
              <h5 className="card-title text-secondary">Nível do Reservatório</h5>
              <h2 className="mb-3">{dadosEsteira.nivel}%</h2>
              <div className="progress" style={{ height: '30px' }}>
                <div 
                  className={`progress-bar progress-bar-striped progress-bar-animated ${corBarra}`} 
                  role="progressbar" 
                  style={{ width: `${dadosEsteira.nivel}%` }} 
                  aria-valuenow={dadosEsteira.nivel} 
                  aria-valuemin="0" 
                  aria-valuemax="100"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default PainelAoVivo;