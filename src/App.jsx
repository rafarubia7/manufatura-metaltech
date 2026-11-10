// src/App.jsx
import React, { useState } from 'react';
import NavBar from './components/NavBar';
import PainelAoVivo from './components/PainelAoVivo';
import GraficosHistorico from './components/GraficosHistorico';
import TabelaManutencoes from './components/TabelaManutencoes';
import './App.css'; // Estilos visuais adicionais

function App() {
  // Memória temporária para controlar qual tela está sendo exibida
  const [telaAtiva, setTelaAtiva] = useState('aoVivo');

  // Função para renderizar o componente correto
  const renderizarTela = () => {
    switch (telaAtiva) {
      case 'aoVivo':
        return <PainelAoVivo />;
      case 'relatorios':
        return <GraficosHistorico />;
      case 'historico':
        return <TabelaManutencoes />;
      default:
        return <PainelAoVivo />;
    }
  };

  return (
    <div className="App">
      <NavBar setTelaAtiva={setTelaAtiva} telaAtiva={telaAtiva} />
      <div className="container-fluid mt-4 px-4"> 
        {renderizarTela()}
      </div>
    </div>
  );

}
export default App;