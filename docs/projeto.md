# PROJETO INTERDISCIPLINAR II

## Situação-Problema

### Plataforma Inteligente de Monitoramento Industrial com Realidade Aumentada

**Modalidade:** Trabalho em equipe, com entrega integrada entre seis unidades curriculares

---

## 1. Contextualização

A empresa fictícia MetalTech Indústria e Manufatura Ltda. opera uma esteira de triagem de peças que ainda é conferida de forma manual: periodicamente, um operador precisa parar a esteira para contar as peças, verificar se são metálicas ou não e observar o nível do reservatório de peças na saída. Esse processo gera atrasos, erros de contagem e não permite que a equipe técnica acompanhe os indicadores da linha em tempo real.

A diretoria contratou a turma de ADS para projetar e construir, de ponta a ponta, uma plataforma digital de monitoramento industrial que integre sensoriamento, nuvem, análise de dados, back-end, front-end e realidade aumentada — reproduzindo, em escala de projeto acadêmico, um cenário típico de Indústria 4.0.

## 2. Situação-Problema

Cada equipe deverá projetar e implementar uma solução completa que permita à MetalTech:

- Coletar automaticamente dados de uma esteira de triagem simulada, usando sensores de distância, indutivo e capacitivo (contagem de peças, presença de peça metálica e presença de peça não metálica);
- Armazenar e disponibilizar esses dados de forma segura e escalável em um ambiente de nuvem;
- Analisar estatisticamente os dados coletados (contagem por tipo de peça, nível do reservatório), identificando padrões e possíveis anomalias;
- Expor os dados por meio de uma API REST bem estruturada e testada;
- Visualizar os dados em um dashboard web responsivo;
- Visualizar os dados também em campo, por meio de um aplicativo de Realidade Aumentada apontado para a esteira/reservatório.

O desafio central do projeto é a integração: cada unidade curricular constrói uma parte do sistema, mas todas as partes precisam conversar entre si por meio de uma API REST comum, definida em conjunto pela equipe logo nas primeiras semanas.

## 3. Objetivo Geral

Desenvolver, de forma integrada, uma solução digital de monitoramento industrial que aplique, em um único produto, os conhecimentos técnicos de Automação Industrial, Ciência de Dados, Computação em Nuvem, Desenvolvimento Back-end, Realidade Aumentada e Frameworks Front-end.

## 4. Unidades Curriculares Envolvidas

| Unidade Curricular | Papel no projeto integrador |
|---|---|
| Automação Industrial | Sensoriamento da esteira de triagem: contagem e classificação de peças (sensores de distância, indutivo e capacitivo) |
| Ciência de Dados | Tratamento estatístico e análise dos dados coletados pela esteira de triagem |
| Computação em Nuvem | Arquitetura de hospedagem, segurança e escalabilidade do sistema |
| Desenvolvimento Back-end | API REST (Java/Spring Boot) que integra sensores, dados e interfaces |
| Eletiva I – Realidade Aumentada | Aplicativo de RA para visualização dos dados da esteira em campo |
| Frameworks Front-end | Dashboard web (React) para monitoramento em tempo real |

## 5. Detalhamento por Unidade Curricular

### 5.1 Automação Industrial

Uma esteira de triagem de peças utilizando os sensores inteligentes disponíveis na disciplina, responsáveis por:

- Sensor de distância: detectar a passagem de cada peça na esteira, para fins de contagem, e/ou medir o nível de peças acumuladas no reservatório de saída;
- Sensor indutivo: identificar a presença de peças metálicas;
- Sensor capacitivo: identificar a presença de peças não metálicas (plástico, madeira etc.), por detectar qualquer material independentemente de ser condutor;
- Combinar as leituras dos sensores indutivo e capacitivo para classificar cada peça (metálica ou não metálica) e enviar os dados periodicamente (requisição HTTP) para a camada de back-end/nuvem.

**Conhecimentos trabalhados:** sensores industriais (indutivo, capacitivo e de distância), controladores industriais, comunicação entre CLP e sistemas de TI, coleta de dados para análise.

### 5.2 Ciência de Dados

A partir da base de dados coletada pela estação de triagem (podendo ser um período simulado com dados gerados/exportados para fins didáticos), a equipe deve:

- Aplicar estatística descritiva (média, mediana, moda, desvio padrão, quartis) sobre a contagem de peças por tipo (metálica/não metálica) e sobre o nível do reservatório;
- Construir gráficos de linha (evolução da contagem e do nível do reservatório ao longo do tempo), gráfico de barras ou dispersão (proporção metálico x não metálico), histograma e boxplot do nível do reservatório;
- Identificar possíveis outliers (ex.: reservatório enchendo mais rápido que o previsto) e propor um critério simples de alerta.

**Conhecimentos trabalhados:** medidas de tendência central e dispersão, análise de séries temporais e correlação, visualização de dados categóricos e quantitativos.

### 5.3 Computação em Nuvem

A equipe deve projetar e justificar a arquitetura de nuvem da solução, definindo:

- O modelo de serviço adotado para hospedar o back-end e o banco de dados (IaaS, PaaS ou SaaS), com justificativa;
- Práticas de segurança aplicadas (controle de usuários, controle de acesso, criptografia dos dados em trânsito/repouso);
- Estratégia de disponibilidade e escalabilidade do serviço hospedado.

**Conhecimentos trabalhados:** modelos de serviço (IaaS/PaaS/SaaS), virtualização de recursos, segurança de dados em nuvem, aspectos econômicos de contratos de nuvem.

### 5.4 Desenvolvimento Back-end

Construir a API REST que centraliza os dados da plataforma, contemplando:

- Modelagem orientada a objetos (classes, herança, encapsulamento, polimorfismo) para representar a esteira, as leituras dos sensores, as peças classificadas e os usuários;
- Operações de CRUD conectadas a um banco de dados;
- Endpoints RESTful documentados, consumidos pelo dashboard React e pelo aplicativo de Realidade Aumentada;
- Testes automatizados e versionamento do código em repositório Git.

**Conhecimentos trabalhados:** programação orientada a objetos, operações fundamentais com banco de dados, Spring Framework/Spring Boot, construção de API front/back-end.

### 5.5 Eletiva I – Realidade Aumentada

Desenvolver uma aplicação de Realidade Aumentada que, ao apontar a câmera de um dispositivo móvel para um marcador (representando a estação de triagem), exiba em sobreposição:

- A contagem atual de peças metálicas e não metálicas, consumida em tempo real da API REST;
- O nível do reservatório de peças, com indicativo visual de alerta quando estiver próximo da capacidade máxima ou fora do padrão identificado pela Ciência de Dados.

**Conhecimentos trabalhados:** fundamentos de RA, marcadores e rastreamento, frameworks e SDKs de RA, integração de dados em aplicações imersivas para Indústria 4.0.

### 5.6 Frameworks Front-end

Construir o dashboard web da plataforma, responsável por:

- Consumir a API REST para exibir, em tempo (quase) real, a contagem de peças por tipo e o nível do reservatório da esteira;
- Apresentar gráficos e indicadores gerados a partir da análise da Ciência de Dados;
- Ser responsivo, organizado em componentes reutilizáveis, com rotas, propriedades e estados bem definidos;
- Utilizar um framework de CSS para a estilização da interface.

**Conhecimentos trabalhados:** tecnologias front-end e responsividade, framework para CSS, framework para front-end (configuração, pacotes NPM, rotas, componentes/propriedades/estados).

## 6. Fluxo Integrado da Solução

O sistema deve seguir, de forma resumida, o seguinte fluxo de dados entre as partes construídas por cada unidade curricular:

1. Automação Industrial → gera e envia os dados da esteira de triagem simulada;
2. Desenvolvimento Back-end → recebe, valida e armazena os dados por meio da API REST;
3. Computação em Nuvem → hospeda o back-end e o banco de dados com segurança e disponibilidade;
4. Ciência de Dados → consome os dados armazenados para gerar análises e indicadores;
5. Frameworks Front-end e Eletiva I (Realidade Aumentada) → consomem a mesma API REST para exibir os dados ao usuário final, em duas interfaces distintas (web e RA).

## 7. Entregável Final Integrado

- Repositório Git com o código-fonte do back-end (API REST) e do front-end (React);
- Firmware da camada de Automação Industrial;
- Aplicativo/projeto de Realidade Aumentada funcional;
- Relatório de Ciência de Dados com gráficos e conclusões;
- Documento de arquitetura de nuvem, com justificativa das escolhas técnicas;
- Apresentação final da equipe, demonstrando o fluxo completo dos dados entre todas as partes do sistema.

## 8. Critérios de Avaliação por Unidade Curricular

| Unidade Curricular | O que é avaliado |
|---|---|
| Automação Industrial | Correção da leitura dos sensores de distância, indutivo e capacitivo, confiabilidade da classificação das peças e do envio de dados, documentação do circuito |
| Ciência de Dados | Qualidade da análise estatística, adequação dos gráficos escolhidos, clareza das conclusões |
| Computação em Nuvem | Justificativa do modelo de serviço escolhido, aplicação de boas práticas de segurança, disponibilidade do serviço hospedado |
| Desenvolvimento Back-end | Qualidade do código orientado a objetos, cobertura de testes, versionamento no Git, documentação da API |
| Eletiva I – Realidade Aumentada | Funcionamento do reconhecimento/rastreamento, integração com dados reais, usabilidade da aplicação |
| Frameworks Front-end | Responsividade, organização de componentes/estado/rotas, integração correta com a API, usabilidade do dashboard |

## 9. Formação das Equipes

Recomenda-se formar equipes de 4 pessoas, de modo que cada integrante possa assumir a responsabilidade principal por uma ou duas unidades curriculares, mantendo reuniões periódicas de alinhamento para garantir que a API REST definida no início do projeto seja respeitada por todas as partes.