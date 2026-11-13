// src/components/PainelAoVivo.jsx
import React, { useEffect, useState } from 'react';
import { buscarEstatisticas, buscarLeituras } from '../services/api';

function PainelAoVivo() {
  const [estatisticas, setEstatisticas] = useState(null);
  const [ultimasLeituras, setUltimasLeituras] = useState([]);
  const [carregando, setCarregando] = useState(true);


  const carregarDados = async () => {
    try {
      const [dadosEst, dadosLeit] = await Promise.all([
        buscarEstatisticas(),
        buscarLeituras(0, 5),
      ]);

      setEstatisticas(dadosEst);

      const lista = Array.isArray(dadosLeit)
        ? dadosLeit
        : Array.isArray(dadosLeit?.content)
        ? dadosLeit.content
        : Array.isArray(dadosLeit?.itens)
        ? dadosLeit.itens
        : Array.isArray(dadosLeit?.dados)
        ? dadosLeit.dados
        : [];

      setUltimasLeituras(lista.slice(0, 5));
    } catch (err) {
      console.error('Erro ao atualizar painel ao vivo:', err);
    } finally {
      setCarregando(false);
    }
  };

  useEffect(() => {
    carregarDados();
    const intervalo = setInterval(carregarDados, 4000);
    return () => clearInterval(intervalo);
  }, []);

  if (carregando && !estatisticas) {
    return <div className="text-theme-secondary py-5 text-center">Carregando telemetria...</div>;
  }

  const metalicas = estatisticas?.totalMetalicas ?? estatisticas?.totalPorTipo?.METALICA ?? 0;
  const naoMetalicas = estatisticas?.totalNaoMetalicas ?? estatisticas?.totalPorTipo?.NAO_METALICA ?? 0;
  const desconhecidas = estatisticas?.totalDesconhecidas ?? estatisticas?.totalPorTipo?.DESCONHECIDA ?? 0;
  const ambas = estatisticas?.totalAmbas ?? estatisticas?.totalPorTipo?.AMBAS ?? 0;
  const alertasAtivos = Number(estatisticas?.alertasAtivos ?? 0);

  const pctMetalica = estatisticas?.percentualPorTipoPeca?.METALICA ?? 0;
  const pctNaoMetalica = estatisticas?.percentualPorTipoPeca?.NAO_METALICA ?? 0;
  const pctDesconhecida = estatisticas?.percentualPorTipoPeca?.DESCONHECIDA ?? 0;

  const leituraMaisRecente = ultimasLeituras[0];
  const nivelAtual =
    leituraMaisRecente?.nivelReservatorio?.percentual ??
    leituraMaisRecente?.nivelReservatorioPercentual ??
    estatisticas?.mediaNivelReservatorio ??
    0;

  const statusReservatorio =
    leituraMaisRecente?.nivelReservatorio?.status ||
    (nivelAtual >= 95
      ? 'CRITICO'
      : nivelAtual > 80
      ? 'ALERTA_ALTO'
      : nivelAtual < 20
      ? 'ALERTA_BAIXO'
      : 'NORMAL');

  const obterEstiloStatus = (status) => {
    switch (status) {
      case 'CRITICO':
        return { texto: 'Crítico (≥ 95%)', classe: 'bg-danger-subtle text-danger', barra: '#ef4444' };
      case 'ALERTA_ALTO':
        return { texto: 'Alerta Alto (> 80%)', classe: 'bg-warning-subtle text-warning-emphasis', barra: '#f59e0b' };
      case 'ALERTA_BAIXO':
        return { texto: 'Nível Baixo (< 20%)', classe: 'bg-info-subtle text-info-emphasis', barra: '#0ea5e9' };
      default:
        return { texto: 'Operação Normal', classe: 'bg-success-subtle text-success', barra: '#10b981' };
    }
  };

  const formatarTipoPeca = (tipo) => {
    const mapa = {
      METALICA: { label: 'Metálica', dot: '#3b82f6' },
      NAO_METALICA: { label: 'Plástica', dot: '#14b8a6' },
      DESCONHECIDA: { label: 'Desconhecida', dot: '#f59e0b' },
      AMBAS: { label: 'Conflito (Ambas)', dot: '#ef4444' },
    };
    return mapa[tipo] || { label: tipo || 'N/A', dot: '#94a3b8' };
  };

  const estiloNivel = obterEstiloStatus(statusReservatorio);

  return (
    <div className="d-flex flex-column gap-4">
      {/* Linha 1: 4 KPIs de Classificação */}
      <div className="row g-3">
        <div className="col-6 col-lg-3">
          <div className="surface-card p-3 p-md-4 h-100">
            <div className="kpi-label">
              <span>Metálicas</span>
              <span className="font-mono-tech text-theme-secondary">{pctMetalica.toFixed(1)}%</span>
            </div>
            <div className="kpi-value num-tabular">{metalicas.toLocaleString('pt-BR')}</div>
            <div className="text-theme-muted mt-2" style={{ fontSize: '0.78rem' }}>
              Sensor indutivo ativo
            </div>
          </div>
        </div>

        <div className="col-6 col-lg-3">
          <div className="surface-card p-3 p-md-4 h-100">
            <div className="kpi-label">
              <span>Plásticas</span>
              <span className="font-mono-tech text-theme-secondary">{pctNaoMetalica.toFixed(1)}%</span>
            </div>
            <div className="kpi-value num-tabular">{naoMetalicas.toLocaleString('pt-BR')}</div>
            <div className="text-theme-muted mt-2" style={{ fontSize: '0.78rem' }}>
              Sensor capacitivo ativo
            </div>
          </div>
        </div>

        <div className="col-6 col-lg-3">
          <div className="surface-card p-3 p-md-4 h-100">
            <div className="kpi-label">
              <span>Desconhecidas</span>
              <span className="font-mono-tech text-theme-secondary">{pctDesconhecida.toFixed(1)}%</span>
            </div>
            <div className="kpi-value num-tabular">{desconhecidas.toLocaleString('pt-BR')}</div>
            <div className="text-theme-muted mt-2" style={{ fontSize: '0.78rem' }}>
              Fora de alinhamento
            </div>
          </div>
        </div>

        <div className="col-6 col-lg-3">
          <div className="surface-card p-3 p-md-4 h-100">
            <div className="kpi-label">
              <span>Ocorrências ativas</span>
            </div>
            <div className={`kpi-value num-tabular ${alertasAtivos > 0 ? 'text-danger' : ''}`}>
              {alertasAtivos.toLocaleString('pt-BR')}
            </div>
            <div className="text-theme-muted mt-2" style={{ fontSize: '0.78rem' }}>
              {alertasAtivos > 0 ? 'Veja a aba Ocorrências' : 'Nenhuma pendência'}
            </div>
          </div>
        </div>
      </div>

      {/* Linha 2: Nível do Reservatório + Últimas Leituras */}
      <div className="row g-3">
        <div className="col-lg-4">
          <div className="surface-card p-4 h-100 d-flex flex-column justify-content-between">
            <div>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <span className="kpi-label mb-0">Nível do Reservatório</span>
                <span
                  className={`badge rounded-pill fw-medium ${estiloNivel.classe}`}
                  style={{ fontSize: '0.75rem', padding: '0.35em 0.75em' }}
                >
                  {estiloNivel.texto}
                </span>
              </div>

              <div className="d-flex align-items-baseline gap-2 my-3">
                <span
                  className="fw-bold num-tabular text-theme-primary"
                  style={{ fontSize: '2.75rem', letterSpacing: '-0.03em' }}
                >
                  {Number(nivelAtual).toFixed(1)}%
                </span>
                <span className="text-theme-secondary" style={{ fontSize: '0.85rem' }}>
                  da capacidade
                </span>
              </div>

              <div
                className="w-100 rounded-pill overflow-hidden"
                style={{ height: '8px', backgroundColor: 'var(--bg-subtle)' }}
              >
                <div
                  className="h-100 rounded-pill"
                  style={{
                    width: `${Math.min(100, Math.max(0, nivelAtual))}%`,
                    backgroundColor: estiloNivel.barra,
                    transition: 'width 0.4s ease, background-color 0.3s ease',
                  }}
                />
              </div>
            </div>

            <div
              className="pt-3 mt-4 border-top border-theme-subtle d-flex justify-content-between text-theme-secondary"
              style={{ fontSize: '0.8rem' }}
            >
              <span>Total processado</span>
              <span className="fw-semibold text-theme-primary num-tabular">
                {(
                  estatisticas?.totalLeituras ??
                  metalicas + naoMetalicas + desconhecidas + ambas
                ).toLocaleString('pt-BR')}{' '}
                peças
              </span>
            </div>
          </div>
        </div>

        <div className="col-lg-8">
          <div className="surface-card h-100 overflow-hidden">
            <div className="p-4 pb-3 d-flex justify-content-between align-items-center">
              <div>
                <h6 className="mb-0 fw-semibold text-theme-primary">Leituras Recentes</h6>
                <small className="text-theme-secondary" style={{ fontSize: '0.8rem' }}>
                  Últimos registros capturados pelos sensores da esteira
                </small>
              </div>
              <span className="font-mono-tech text-theme-muted">Atualização automática</span>
            </div>

            <div className="table-responsive">
              <table className="table table-clean">
                <thead>
                  <tr>
                    <th>Horário</th>
                    <th>Classificação</th>
                    <th>Reservatório</th>
                    <th>Dispositivo</th>
                  </tr>
                </thead>
                <tbody>
                  {ultimasLeituras.length === 0 ? (
                    <tr>
                      <td colSpan="4" className="text-center text-theme-secondary py-4">
                        Aguardando envio de leituras pela esteira...
                      </td>
                    </tr>
                  ) : (
                    ultimasLeituras.map((item, idx) => {
                      const tipo = item.peca?.tipo ?? item.tipoPeca;
                      const infoPeca = formatarTipoPeca(tipo);
                      const pct =
                        item.nivelReservatorio?.percentual ?? item.nivelReservatorioPercentual ?? 0;

                      return (
                        <tr key={item.id || idx}>
                          <td className="font-mono-tech text-theme-secondary">
                            {item.timestamp
                              ? new Date(item.timestamp).toLocaleTimeString('pt-BR')
                              : '--:--:--'}
                          </td>
                          <td>
                            <span className="d-inline-flex align-items-center gap-2 fw-medium">
                              <span
                                style={{
                                  width: '8px',
                                  height: '8px',
                                  borderRadius: '50%',
                                  backgroundColor: infoPeca.dot,
                                }}
                              />
                              {infoPeca.label}
                            </span>
                          </td>
                          <td className="num-tabular fw-medium">{Number(pct).toFixed(1)}%</td>
                          <td className="font-mono-tech text-theme-muted">
                            {item.dispositivoId || '—'}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default PainelAoVivo;