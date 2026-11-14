// src/components/TabelaManutencoes.jsx
import React, { useEffect, useState } from 'react';
import { buscarAlertas, resolverAlerta } from '../services/api';

const TIPOS_ALERTA = {
  RESERVATORIO_CRITICO: { label: 'Reservatório crítico', classe: 'bg-danger-subtle text-danger' },
  RESERVATORIO_ALTO: { label: 'Reservatório alto', classe: 'bg-warning-subtle text-warning-emphasis' },
  RESERVATORIO_BAIXO: { label: 'Reservatório baixo', classe: 'bg-info-subtle text-info-emphasis' },
  ENCHIMENTO_RAPIDO: { label: 'Enchimento rápido', classe: 'bg-warning-subtle text-warning-emphasis' },
  CONTAGEM_ANOMALA: { label: 'Conflito de sensores', classe: 'bg-secondary-subtle text-secondary-emphasis' },
};

const formatarTipoAlerta = (tipo) =>
  TIPOS_ALERTA[tipo] || { label: tipo || 'Alerta', classe: 'bg-secondary-subtle text-secondary-emphasis' };

const formatarDataHora = (valor) => (valor ? new Date(valor).toLocaleString('pt-BR') : '—');

function TabelaManutencoes({ aoAlterarAlertas }) {
  const [alertas, setAlertas] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [feedback, setFeedback] = useState('');


  const carregarAlertas = async () => {
    try {
      const dados = await buscarAlertas();
      setAlertas(Array.isArray(dados) ? dados : []);
    } catch (err) {
      console.error('Erro ao carregar alertas:', err);
    } finally {
      setCarregando(false);
    }
  };

  useEffect(() => {
    carregarAlertas();
    // Mantém a tabela em sincronia com o contador do cabeçalho.
    const intervalo = setInterval(carregarAlertas, 10000);
    return () => clearInterval(intervalo);
  }, []);

  const handleResolver = async (id) => {
    try {
      await resolverAlerta(id);
      setFeedback(`Ocorrência #${id} marcada como resolvida.`);
      carregarAlertas();
      aoAlterarAlertas?.(); // atualiza o contador do cabeçalho na hora
      setTimeout(() => setFeedback(''), 3000);
    } catch (err) {
      console.error('Erro ao resolver alerta:', err);
    }
  };

  if (carregando) {
    return <div className="text-theme-secondary py-5 text-center">Carregando ocorrências...</div>;
  }

  return (
    <div className="surface-card overflow-hidden">
      <div className="p-4 d-flex flex-wrap justify-content-between align-items-center gap-2 border-bottom border-theme-subtle">
        <div>
          <h6 className="mb-0 fw-semibold text-theme-primary">Ocorrências e Alertas Operacionais</h6>
          <small className="text-theme-secondary" style={{ fontSize: '0.8rem' }}>
            Monitoramento de capacidade do reservatório e anomalias de leitura
          </small>
        </div>

        <button
          type="button"
          className="theme-toggle-btn px-3"
          onClick={carregarAlertas}
        >
          Atualizar
        </button>
      </div>

      {feedback && (
        <div className="px-4 pt-3">
          <div className="alert alert-success py-2 px-3 mb-0" style={{ fontSize: '0.85rem' }}>
            {feedback}
          </div>
        </div>
      )}

      {alertas.length === 0 ? (
        <div className="p-5 text-center">
          <div
            className="mx-auto mb-3 d-flex align-items-center justify-content-center rounded-circle bg-success-subtle text-success"
            style={{ width: '44px', height: '44px' }}
          >
            <svg width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2.2" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h6 className="fw-semibold text-theme-primary mb-1">Nenhuma ocorrência pendente</h6>
          <p className="text-theme-secondary mb-0" style={{ fontSize: '0.85rem' }}>
            A esteira e os níveis do reservatório estão operando dentro dos parâmetros normais.
          </p>
        </div>
      ) : (
        <div className="table-responsive">
          <table className="table table-clean">
            <thead>
              <tr>
                <th>ID</th>
                <th>Registro</th>
                <th>Classificação</th>
                <th>Descrição</th>
                <th>Situação</th>
                <th className="text-end">Ação</th>
              </tr>
            </thead>
            <tbody>
              {alertas.map((alerta) => {
                const tipo = formatarTipoAlerta(alerta.tipoAlerta);
                return (
                <tr key={alerta.id}>
                  <td className="font-mono-tech text-theme-secondary">#{alerta.id}</td>
                  <td className="font-mono-tech text-theme-secondary text-nowrap">
                    {formatarDataHora(alerta.dataHoraAlerta)}
                  </td>
                  <td>
                    <span className={`badge fw-medium ${tipo.classe}`} title={alerta.tipoAlerta}>
                      {tipo.label}
                    </span>
                  </td>
                  <td className="text-theme-primary">{alerta.mensagem}</td>
                  <td>
                    {alerta.resolvido ? (
                      <span className="badge bg-success-subtle text-success fw-medium">Resolvido</span>
                    ) : (
                      <span className="badge bg-danger-subtle text-danger fw-medium">Pendente</span>
                    )}
                  </td>
                  <td className="text-end">
                    {!alerta.resolvido && (
                      <button
                        type="button"
                        className="btn btn-sm btn-primary fw-medium px-3"
                        style={{ borderRadius: '7px', fontSize: '0.8rem' }}
                        onClick={() => handleResolver(alerta.id)}
                      >
                        Marcar como resolvido
                      </button>
                    )}
                  </td>
                </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default TabelaManutencoes;