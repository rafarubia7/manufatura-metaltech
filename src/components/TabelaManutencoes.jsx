// src/components/TabelaManutencoes.jsx
import React from 'react';

function TabelaManutencoes() {
  // Dados de teste para a tabela[cite: 2]
  const historico = [
    { id: 1, data: '10/11/2026', hora: '14:30', evento: 'Reservatório Cheio (85%)', status: 'Aviso' },
    { id: 2, data: '10/11/2026', hora: '10:15', evento: 'Falha na conexão com sensor', status: 'Erro' },
    { id: 3, data: '09/11/2026', hora: '08:00', evento: 'Início do turno - Sistema OK', status: 'Info' },
  ];

  return (
    <div className="card shadow-sm">
      <div className="card-header bg-white">
        <h4 className="mb-0 text-secondary">Histórico e Avisos da Esteira</h4>
      </div>
      <div className="card-body">
        <table className="table table-striped table-hover">
          <thead className="table-dark">
            <tr>
              <th>Data</th>
              <th>Hora</th>
              <th>Evento</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {historico.map((item) => (
              <tr key={item.id}>
                <td>{item.data}</td>
                <td>{item.hora}</td>
                <td>{item.evento}</td>
                <td>
                  <span className={`badge ${
                    item.status === 'Erro' ? 'bg-danger' : 
                    item.status === 'Aviso' ? 'bg-warning text-dark' : 'bg-info text-dark'
                  }`}>
                    {item.status}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default TabelaManutencoes;