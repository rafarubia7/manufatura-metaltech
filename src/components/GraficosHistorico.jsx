// src/components/GraficosHistorico.jsx
import React from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, BarChart, Bar,
} from 'recharts';

// Cores espelham as variáveis do App.css (o Recharts precisa de valores literais nos atributos SVG)
const COR_PRIMARIA = '#2563eb';
const COR_NEUTRA = '#a1a1aa';
const COR_EIXO = '#71717a';
const COR_GRADE = '#f0f0f2';

const estiloTooltip = {
  background: '#ffffff',
  border: '1px solid #eaeaea',
  borderRadius: 10,
  boxShadow: '0 8px 24px rgba(0, 0, 0, 0.06)',
  fontSize: 13,
  padding: '8px 12px',
};

const estiloEixo = { fontSize: 12, fill: COR_EIXO };

function GraficosHistorico() {
  const dadosLinha = [
    { hora: '08:00', pecas: 12 },
    { hora: '09:00', pecas: 25 },
    { hora: '10:00', pecas: 45 },
    { hora: '11:00', pecas: 30 },
    { hora: '12:00', pecas: 50 },
  ];

  const dadosProporcao = [
    { nome: 'Metal (60%)', quantidade: 60, fill: COR_PRIMARIA },
    { nome: 'Plástico (40%)', quantidade: 40, fill: COR_NEUTRA },
  ];

  return (
    <div className="row g-4">
      <div className="col-12">
        <h4 className="section-title">Evolução de peças por hora</h4>
        <div className="panel">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={dadosLinha} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
              <CartesianGrid stroke={COR_GRADE} vertical={false} />
              <XAxis dataKey="hora" tick={estiloEixo} axisLine={false} tickLine={false} dy={8} />
              <YAxis tick={estiloEixo} axisLine={false} tickLine={false} />
              <Tooltip
                contentStyle={estiloTooltip}
                cursor={{ stroke: '#e4e4e7', strokeWidth: 1 }}
              />
              <Line
                type="monotone"
                dataKey="pecas"
                name="Total de peças"
                stroke={COR_PRIMARIA}
                strokeWidth={2.5}
                dot={{ r: 3, fill: '#fff', stroke: COR_PRIMARIA, strokeWidth: 2 }}
                activeDot={{ r: 5 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="col-md-6">
        <h4 className="section-title">Proporção (metal vs plástico)</h4>
        <div className="panel">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={dadosProporcao} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
              <CartesianGrid stroke={COR_GRADE} vertical={false} />
              <XAxis dataKey="nome" tick={estiloEixo} axisLine={false} tickLine={false} dy={8} />
              <YAxis tick={estiloEixo} axisLine={false} tickLine={false} />
              {/* CORREÇÃO 1: remove o retângulo cinza no hover */}
              <Tooltip
                cursor={{ fill: 'transparent' }}
                contentStyle={estiloTooltip}
              />
              <Bar dataKey="quantidade" name="Quantidade" radius={[8, 8, 0, 0]} maxBarSize={56} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="col-md-6">
        <h4 className="section-title">Indicador do tanque</h4>
        {/* CORREÇÃO 2: mesma altura do gráfico (.panel), sem display-4 / h-100 */}
        <div className="panel stat-panel">
          <span className="stat-label">Tempo médio de enchimento</span>
          <div className="stat-value">
            45<span className="stat-unit">min</span>
          </div>
          <span className="stat-caption">Última análise de dispersão</span>
        </div>
      </div>
    </div>
  );
}

export default GraficosHistorico;