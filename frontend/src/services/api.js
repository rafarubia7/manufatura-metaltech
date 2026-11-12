// src/services/api.js
// Sem VITE_API_URL, usa o mesmo host em que a página foi aberta (localhost ou o IP do PC do back-end),
// na porta 8080. Assim não é preciso trocar o IP quando ele muda.
const API_URL = import.meta.env.VITE_API_URL || `${window.location.protocol}//${window.location.hostname}:8080`;
const BASE_URL = `${API_URL}/api/v1`;

const extrairLista = (payload) => {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.content)) return payload.content;
  if (Array.isArray(payload?.itens)) return payload.itens;
  if (Array.isArray(payload?.dados)) return payload.dados;
  return [];
};

const normalizarEstatisticas = (payload = {}) => ({
  totalPorTipo: {
    METALICA: Number(payload.totalMetalicas ?? payload.totalPorTipo?.METALICA ?? payload.totalPorTipo?.metalica ?? 0),
    NAO_METALICA: Number(
      payload.totalNaoMetalicas ??
        payload.totalPorTipo?.NAO_METALICA ??
        payload.totalPorTipo?.naoMetalica ??
        payload.totalPorTipo?.nao_metalica ??
        0
    ),
    DESCONHECIDA: Number(payload.totalDesconhecidas ?? payload.totalPorTipo?.DESCONHECIDA ?? payload.totalPorTipo?.desconhecida ?? 0),
    AMBAS: Number(payload.totalAmbas ?? payload.totalPorTipo?.AMBAS ?? payload.totalPorTipo?.ambas ?? 0),
  },
  nivelAtualPercentual: Number(
    payload.nivelAtualPercentual ??
      payload.nivelReservatorioAtual ??
      payload.nivelReservatorio?.percentual ??
      payload.nivelReservatorioAtualPercentual ??
      0
  ),
  mediaNivel: Number(payload.mediaNivelReservatorio ?? payload.mediaNivel ?? 0),
  medianaNivel: Number(payload.medianaNivelReservatorio ?? payload.medianaNivel ?? 0),
  minimoNivel: Number(payload.minimoNivelReservatorio ?? payload.minimoNivel ?? 0),
  maximoNivel: Number(payload.maximoNivelReservatorio ?? payload.maximoNivel ?? 0),
  percentualPorTipoPeca: payload.percentualPorTipoPeca ?? {},
  alertasAtivos: payload.alertasAtivos ?? 0,
  periodoInicio: payload.periodoInicio ?? null,
  periodoFim: payload.periodoFim ?? null,
});

export const buscarEstatisticas = async () => {
  const resposta = await fetch(`${BASE_URL}/leituras/estatisticas`);
  if (!resposta.ok) throw new Error('Erro ao buscar estatísticas');
  const dados = await resposta.json();
  return normalizarEstatisticas(dados);
};

export const buscarLeituras = async (page = 0, size = 20) => {
  const resposta = await fetch(`${BASE_URL}/leituras?page=${page}&size=${size}`);
  if (!resposta.ok) throw new Error('Erro ao buscar leituras');
  const dados = await resposta.json();

  return extrairLista(dados);
};

export const buscarAlertas = async () => {
  const resposta = await fetch(`${BASE_URL}/leituras/alertas`);
  if (!resposta.ok) throw new Error('Erro ao buscar alertas');
  return resposta.json();
};

export const resolverAlerta = async (id) => {
  const resposta = await fetch(`${BASE_URL}/leituras/alertas/${id}/resolver`, {
    method: 'PUT',
  });

  if (!resposta.ok) {
    throw new Error('Erro ao resolver alerta');
  }

  if (resposta.status === 204) {
    return null;
  }

  const texto = await resposta.text();
  return texto ? JSON.parse(texto) : null;
};

