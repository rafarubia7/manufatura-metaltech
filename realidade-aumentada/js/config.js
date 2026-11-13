// =============================================================
// CONFIGURAÇÃO DO APP — altere SOMENTE aqui.
// Os dados desta versão são SIMULADOS (fictícios) e a RA funciona
// de forma independente, sem depender de nenhum outro sistema.
// =============================================================

// true = exibe o selo de dados simulados (fictícios)
const MODO_TESTE = true;

// Intervalo entre atualizações dos dados, em milissegundos.
const INTERVALO_MS = 3000;

// Tempo (ms) que os painéis continuam na tela depois que a câmera
// perde a esteira. Evita que pisquem quando o celular treme.
const TOLERANCIA_PERDA_MS = 1500;

// Limiares do nível do reservatório (em %), usados para o status.
const LIMITE_ALERTA_ALTO = 80;   // a partir daqui: "ALERTA_ALTO" (amarelo)
const LIMITE_CRITICO = 95;       // a partir daqui: "CRITICO" (vermelho)
const LIMITE_ALERTA_BAIXO = 10;  // abaixo daqui: "ALERTA_BAIXO" (apenas informativo)

// Aumento (pontos percentuais) entre duas atualizações que caracteriza
// enchimento acima do padrão.
const SALTO_ENCHIMENTO_RAPIDO = 5;
