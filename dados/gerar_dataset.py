import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import random
import os

def gerar_dataset_simulado(registros=10000, seed=42):
    """
    Gera dataset simulado de leituras da esteira de triagem
    
    Args:
        registros: Número de registros a gerar
        seed: Semente para reproducibilidade
    """
    random.seed(seed)
    np.random.seed(seed)
    
    # Parâmetros de simulação
    inicio = datetime.now() - timedelta(days=30)
    capacidade_maxima = 100.0
    
    dados = []
    nivel_atual = 50.0  # Inicia com 50%
    
    for i in range(registros):
        timestamp = inicio + timedelta(minutes=i * 1.5)  # 1.5 min entre leituras
        
        # Simulação de sensores
        distancia = random.uniform(5.0, 20.0)
        
        # Sensor indutivo (metal)
        indutivo_detectou = random.random() > 0.3
        indutivo_intensidade = random.uniform(0.7, 1.0) if indutivo_detectou else random.uniform(0.0, 0.3)
        
        # Sensor capacitivo (não metal)
        capacitivo_detectou = random.random() > 0.3 and not indutivo_detectou
        capacitivo_intensidade = random.uniform(0.7, 1.0) if capacitivo_detectou else random.uniform(0.0, 0.3)
        
        # Classificação da peça
        if indutivo_detectou and capacitivo_detectou:
            tipo_peca = "AMBAS"
        elif indutivo_detectou:
            tipo_peca = "METALICA"
        elif capacitivo_detectou:
            tipo_peca = "NAO_METALICA"
        else:
            tipo_peca = "DESCONHECIDA"
        
        # Simulação do reservatório (incrementa/decrementa aleatoriamente)
        if random.random() > 0.5:
            nivel_atual += random.uniform(0.5, 3.0)
        else:
            nivel_atual -= random.uniform(0.2, 1.5)
        
        nivel_atual = max(0, min(capacidade_maxima, nivel_atual))
        
        # Status do reservatório
        percentual = (nivel_atual / capacidade_maxima) * 100
        if percentual < 20:
            status = "ALERTA_BAIXO"
        elif percentual > 80:
            status = "ALERTA_ALTO"
        elif percentual > 95:
            status = "CRITICO"
        else:
            status = "NORMAL"
        
        dados.append({
            'timestamp': timestamp.isoformat(),
            'distancia_cm': round(distancia, 2),
            'indutivo_detectou_metal': indutivo_detectou,
            'indutivo_intensidade': round(indutivo_intensidade, 3),
            'capacitivo_detectou_nao_metal': capacitivo_detectou,
            'capacitivo_intensidade': round(capacitivo_intensidade, 3),
            'tipo_peca': tipo_peca,
            'nivel_reservatorio_percentual': round(percentual, 2),
            'status_reservatorio': status,
            'dispositivo_id': 'ESP32-001',
            'versao_firmware': '1.0.0'
        })
    
    df = pd.DataFrame(dados)
    
    # Garantir que o diretório existe
    os.makedirs('dados', exist_ok=True)
    
    # Salvar CSV
    csv_path = 'dados/leituras_simuladas.csv'
    df.to_csv(csv_path, index=False)
    
    print(f"Dataset gerado: {len(df)} registros")
    print(f"Salvo em: {csv_path}")
    print(f"Período: {df['timestamp'].min()} até {df['timestamp'].max()}")
    print(f"Tipos de peça:\n{df['tipo_peca'].value_counts()}")
    print(f"Status reservatório:\n{df['status_reservatorio'].value_counts()}")
    
    return df

def gerar_resumo_analitico(df: pd.DataFrame) -> str:
    """Cria um relatório resumido em texto para facilitar a documentação da CIDA."""
    resumo = []
    resumo.append("# Relatório inicial de análise - MetalTech")
    resumo.append("")
    resumo.append(f"Total de registros: {len(df)}")
    resumo.append(f"Período analisado: {df['timestamp'].min()} até {df['timestamp'].max()}")
    resumo.append("")
    resumo.append("## Distribuição por tipo de peça")
    resumo.append(df['tipo_peca'].value_counts().to_string())
    resumo.append("")
    resumo.append("## Distribuição por status do reservatório")
    resumo.append(df['status_reservatorio'].value_counts().to_string())
    resumo.append("")
    resumo.append("## Estatística descritiva do nível do reservatório")
    nivel = df['nivel_reservatorio_percentual']
    resumo.append(f"Média: {nivel.mean():.2f}%")
    resumo.append(f"Mediana: {nivel.median():.2f}%")
    resumo.append(f"Desvio padrão: {nivel.std():.2f}%")
    resumo.append(f"Mínimo: {nivel.min():.2f}%")
    resumo.append(f"Máximo: {nivel.max():.2f}%")
    resumo.append(f"Quartis: {nivel.quantile([0.25, 0.5, 0.75]).to_dict()}")
    resumo.append("")
    resumo.append("## Observações iniciais")
    resumo.append("- A distribuição do reservatório indica maior concentração nas faixas normais e de alerta, com ocorrências de picos críticos esperados em operações em lote.")
    resumo.append("- A classificação por tipo de peça mostra a proporção típica do fluxo simulado entre peças metálicas e não metálicas.")
    resumo.append("- Conflitos de sensores (tipo AMBAS) devem ser tratados como eventos de validação e possíveis falhas de instrumentação.")
    return "\n".join(resumo)

if __name__ == "__main__":
    # Gerar dataset com 10k registros
    df = gerar_dataset_simulado(10000)
    
    # Salvar também como parquet para análise mais rápida
    df.to_parquet('dados/leituras_simuladas.parquet', index=False)
    print("Dataset salvo também como Parquet")
    
    # Gerar e salvar resumo analítico
    resumo = gerar_resumo_analitico(df)
    with open('dados/resumo_analitico.txt', 'w') as f:
        f.write(resumo)
    print("Resumo analítico salvo em: dados/resumo_analitico.txt")