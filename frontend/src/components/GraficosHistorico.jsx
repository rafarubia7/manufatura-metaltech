// src/components/GraficosHistorico.jsx
import React, { useEffect, useState } from 'react';
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  Cell,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  ReferenceLine,
} from 'recharts';
import { buscarLeituras, buscarEstatisticas } from '../services/api';

function GraficosHistorico({ tema = 'light' }) {
  const [leituras, setLeituras] = useState([]);
  const [estatisticas, setEstatisticas] = useState(null);
  const [carregando, setCarregando] = useState(true);

  const isDark = tema === 'dark';
  const gridColor = isDark ? '#1e293b' : '#f1f5f9';
  const tickColor = isDark ? '#94a3b8' : '#64748b';
  const tooltipStyle = {
    backgroundColor: isDark ? '#111827' : '#ffffff',
    borderColor: isDark ? '#374151' : '#e2e8f0',
    color: isDark ? '#f8fafc' : '#0f172a',
    borderRadius: '8px',
    fontSize: '0.85rem',
  };

  const tooltipItemStyle = {
    color: isDark ? '#f8fafc' : '#0f172a',
  };

  useEffect(() => {
    carregarDados();
  }, []);

  const carregarDados = async () => {
    try {
      const [dadosLeituras, dadosEstatisticas] = await Promise.all([
        buscarLeituras(0, 25),
        buscarEstatisticas(),
      ]);

      const listaBruta = Array.isArray(dadosLeituras)
        ? dadosLeituras
        : Array.isArray(dadosLeituras?.content)
        ? dadosLeituras.content
        : Array.isArray(dadosLeituras?.itens)
        ? dadosLeituras.itens
        : Array.isArray(dadosLeituras?.dados)
        ? dadosLeituras.dados
        : [];

      const leiturasFormatadas = listaBruta
        .map((l) => ({
          hora: l.timestamp
            ? new Date(l.timestamp).toLocaleTimeString('pt-BR', {
                hour: '2-digit',
                minute: '2-digit',
              })
            : '',
          nivel: Number(
            (l.nivelReservatorio?.percentual ?? l.nivelReservatorioPercentual ?? 0).toFixed(1)
          ),
        }))
        .reverse();

      setLeituras(leiturasFormatadas);
      setEstatisticas(dadosEstatisticas);
    } catch (err) {
      console.error('Erro ao carregar dados históricos:', err);
    } finally {
      setCarregando(false);
    }
  };

  if (carregando) {
    return <div className="text-theme-secondary py-5 text-center">Carregando análise histórica...</div>;
  }

  const dadosBarra = [
    {
      nome: 'Metálica',
      quantidade: estatisticas?.totalMetalicas ?? estatisticas?.totalPorTipo?.METALICA ?? 0,
      cor: '#3b82f6',
    },
    {
      nome: 'Plástica',
      quantidade: estatisticas?.totalNaoMetalicas ?? estatisticas?.totalPorTipo?.NAO_METALICA ?? 0,
      cor: '#14b8a6',
    },
    {
      nome: 'Desconhecida',
      quantidade: estatisticas?.totalDesconhecidas ?? estatisticas?.totalPorTipo?.DESCONHECIDA ?? 0,
      cor: '#f59e0b',
    },
    {
      nome: 'Conflito',
      quantidade: estatisticas?.totalAmbas ?? estatisticas?.totalPorTipo?.AMBAS ?? 0,
      cor: '#ef4444',
    },
  ];

  const media = estatisticas?.mediaNivelReservatorio ?? estatisticas?.mediaNivel ?? 0;
  const mediana = estatisticas?.medianaNivelReservatorio ?? 0;
  const minimo = estatisticas?.minimoNivelReservatorio ?? 0;
  const maximo = estatisticas?.maximoNivelReservatorio ?? 0;

  return (
    <div className="d-flex flex-column gap-4">
      <div className="surface-card p-4">
        <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
          <div>
            <h6 className="mb-0 fw-semibold text-theme-primary">Evolução do Nível do Reservatório</h6>
            <small className="text-theme-secondary" style={{ fontSize: '0.8rem' }}>
              Comportamento percentual nas últimas leituras registradas
            </small>
          </div>
          <div className="d-flex align-items-center gap-3" style={{ fontSize: '0.78rem' }}>
            <span className="text-theme-secondary d-inline-flex align-items-center gap-1">
              <span style={{ width: 10, height: 2, backgroundColor: '#f59e0b', display: 'inline-block' }} />
              Alerta (80%)
            </span>
            <span className="text-theme-secondary d-inline-flex align-items-center gap-1">
              <span style={{ width: 10, height: 2, backgroundColor: '#ef4444', display: 'inline-block' }} />
              Crítico (95%)
            </span>
          </div>
        </div>

        <div style={{ width: '100%', height: 290 }}>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={leituras} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
              <defs>
                <linearGradient id="gradienteNivel" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#3b82f6" stopOpacity={isDark ? 0.25 : 0.16} />
                  <stop offset="95%" stopColor="#3b82f6" stopOpacity={0.0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={gridColor} />
              <XAxis
                dataKey="hora"
                axisLine={false}
                tickLine={false}
                tick={{ fill: tickColor, fontSize: 12 }}
              />
              <YAxis
                domain={[0, 100]}
                axisLine={false}
                tickLine={false}
                tick={{ fill: tickColor, fontSize: 12 }}
              />
              <Tooltip contentStyle={tooltipStyle} itemStyle={tooltipItemStyle} />
              <ReferenceLine y={80} stroke="#f59e0b" strokeDasharray="4 4" />
              <ReferenceLine y={95} stroke="#ef4444" strokeDasharray="4 4" />
              <Area
                type="monotone"
                dataKey="nivel"
                name="Nível (%)"
                stroke="#3b82f6"
                strokeWidth={2}
                fillOpacity={1}
                fill="url(#gradienteNivel)"
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="row g-3">
        <div className="col-lg-7">
          <div className="surface-card p-4 h-100">
            <div className="mb-4">
              <h6 className="mb-0 fw-semibold text-theme-primary">Distribuição por Tipo de Peça</h6>
              <small className="text-theme-secondary" style={{ fontSize: '0.8rem' }}>
                Volume acumulado por categoria identificada
              </small>
            </div>

            <div style={{ width: '100%', height: 240 }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={dadosBarra} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={gridColor} />
                  <XAxis
                    dataKey="nome"
                    axisLine={false}
                    tickLine={false}
                    tick={{ fill: tickColor, fontSize: 12 }}
                  />
                  <YAxis
                    axisLine={false}
                    tickLine={false}
                    tick={{ fill: tickColor, fontSize: 12 }}
                  />
                  <Tooltip
                    cursor={{ fill: isDark ? '#1e293b' : '#f8fafc' }}
                    contentStyle={tooltipStyle}
                    itemStyle={tooltipItemStyle}
                  />
                  <Bar dataKey="quantidade" name="Peças" radius={[6, 6, 0, 0]} maxBarSize={48}>
                    {dadosBarra.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.cor} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        <div className="col-lg-5">
          <div className="surface-card p-4 h-100 d-flex flex-column justify-content-between">
            <div>
              <h6 className="mb-0 fw-semibold text-theme-primary">Resumo do Reservatório</h6>
              <small className="text-theme-secondary" style={{ fontSize: '0.8rem' }}>
                Indicadores estatísticos do período ativo
              </small>

              <div className="my-4">
                <span className="text-theme-secondary d-block" style={{ fontSize: '0.8rem' }}>
                  Ocupação Média
                </span>
                <span
                  className="fw-bold num-tabular text-theme-primary"
                  style={{ fontSize: '2.5rem', letterSpacing: '-0.03em' }}
                >
                  {Number(media).toFixed(1)}%
                </span>
              </div>
            </div>

            <div className="row g-2 pt-3 border-top border-theme-subtle">
              <div className="col-4">
                <span className="text-theme-secondary d-block" style={{ fontSize: '0.75rem' }}>
                  Mediana
                </span>
                <span className="fw-semibold num-tabular text-theme-primary">
                  {Number(mediana).toFixed(1)}%
                </span>
              </div>
              <div className="col-4">
                <span className="text-theme-secondary d-block" style={{ fontSize: '0.75rem' }}>
                  Mínimo
                </span>
                <span className="fw-semibold num-tabular text-theme-primary">
                  {Number(minimo).toFixed(1)}%
                </span>
              </div>
              <div className="col-4">
                <span className="text-theme-secondary d-block" style={{ fontSize: '0.75rem' }}>
                  Máximo
                </span>
                <span className="fw-semibold num-tabular text-theme-primary">
                  {Number(maximo).toFixed(1)}%
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default GraficosHistorico;