# RA Esteira — MetalTech

Aplicação **WebAR** do Projeto Integrador da MetalTech. Ao apontar a câmera do celular para a **esteira de triagem real**, o app reconhece a esteira e mostra por cima dela:

1. **Contagem de peças**: metálicas, não metálicas e total;
2. **Nível do reservatório**: porcentagem com barra, cor e texto de status
   - 🟢 verde, **Normal**: abaixo de 80%
   - 🟡 amarelo, **Atenção: perto da capacidade**: a partir de 80%
   - 🔴 vermelho, **Crítico: esvaziar o reservatório**: a partir de 95%
   - abaixo de 10% aparece **Normal (nível baixo)**, apenas informativo e sem cor de alerta
3. **Faixa "Enchimento acima do padrão (> 5 %/min)"**: faixa vermelha piscando no painel de nível quando o reservatório sobe mais de 5 pontos percentuais entre duas atualizações. Ela some na atualização seguinte, quando o enchimento volta ao ritmo normal;
4. **"Atualizado há X s"**: abaixo do nível, mostra há quanto tempo chegou a última leitura e avança a cada segundo (*"Atualizado agora"* nos primeiros 2 s).

Quando a esteira sai da câmera, os painéis ficam mais 1,5 s na tela (para não piscarem) e depois somem; o app volta a mostrar *"Aponte a câmera para a esteira…"*.

Os painéis podem ser **arrastados com o dedo** para qualquer lugar da tela e **aumentados ou diminuídos** com os botões **−** / **+** (no canto de cima de cada painel) ou com **pinça de dois dedos**. Posição e tamanho ficam salvos no celular, e o botão **↺ Organizar** volta tudo ao lugar e tamanho iniciais.

O reconhecimento usa o MindAR, que compara a câmera com **fotos da esteira** compiladas no `assets/target.mind`. Por isso o usuário precisa apontar o celular para o angulo de onde a foto foi tirada (veja [Como fazer o app reconhecer a esteira real](#como-fazer-o-app-reconhecer-a-esteira-real)).

> **Aviso importante:** os dados exibidos nesta aplicação (contagem de peças e nível do reservatório) são **FICTÍCIOS**, gerados por simulação, e mudam a cada 3 segundos. Não há integração com dados reais da esteira. Esta é a **versão final** da aplicação de Realidade Aumentada do projeto e serve para demonstrar o conceito e a experiência de uso.

## Como apontar a câmera para a esteira

Para os painéis aparecerem, a câmera do celular deve estar posicionada no mesmo ângulo da foto usada como referência. Use a imagem abaixo como guia de posição, altura e enquadramento.

<img width="4080" height="2296" alt="image" src="https://github.com/user-attachments/assets/1e8d1aea-cc57-4665-a21e-6b1182a6011d" />


Dicas de posicionamento:

- Fique na mesma posição de onde a foto de referência foi tirada.
- Mantenha o celular na altura indicada na imagem-guia.
- Enquadre toda a esteira, como na imagem.
- Evite reflexos e luz forte direta sobre a esteira.

## Tecnologias

- HTML5, CSS3 e JavaScript puro (sem build, sem npm, sem framework)
- [A-Frame 1.6.0](https://aframe.io/)
- [MindAR 1.2.5](https://hiukim.github.io/mind-ar-js-doc/) (rastreamento de imagem)
- GitHub Pages (hospedagem estática com HTTPS)

## Estrutura de pastas

```
realidade-aumentada/
├── index.html        # cena de RA + painéis sobrepostos
├── README.md
├── assets/
│   └── target.mind   # foto(s) da esteira compiladas para o MindAR
├── css/
│   └── style.css     # estilos dos painéis, selo e barra de nível
└── js/
    ├── config.js     # INTERVALO_MS, TOLERANCIA_PERDA_MS e limiares do nível
    ├── dados.js      # obterDados(): gera os dados fictícios, o status e o alerta de enchimento
    ├── arrastar.js   # mover/redimensionar os painéis e lembrar posição e tamanho
    └── main.js       # eventos targetFound/targetLost/arError, status, faixa e "Atualizado há"
```

## Como testar na esteira

1. Abra o endereço do GitHub Pages no celular (Chrome no Android ou Safari no iPhone).
2. Permita o acesso à câmera.
3. Fique na mesma posição de onde a foto da esteira foi tirada e aponte o celular para a **esteira**, enquadrando-a como na foto.
   - Sem acesso à esteira? Dá para testar apontando para a própria foto na tela do computador.
4. Confira:
   - [ ] O selo amarelo **⚠ DADOS DE TESTE (SIMULADOS)** aparece no topo;
   - [ ] Os valores exibidos são fictícios (simulados), conforme o aviso no topo deste README;
   - [ ] Ao reconhecer a esteira, aparecem os dois painéis (contagem e nível);
   - [ ] Os números mudam a cada 3 segundos;
   - [ ] Dá para arrastar cada painel com o dedo;
   - [ ] Os botões **−** / **+** e a pinça com dois dedos diminuem/aumentam o painel;
   - [ ] Ao recarregar a página, os painéis continuam no lugar e no tamanho escolhidos;
   - [ ] O botão **↺ Organizar** volta os painéis para a posição e o tamanho iniciais;
   - [ ] A barra muda de cor (verde → amarelo → vermelho) conforme o nível;
   - [ ] A barra fica amarela a partir de 80% e vermelha a partir de 95%;
   - [ ] De vez em quando aparece a faixa piscando **Enchimento acima do padrão (> 5 %/min)**, que some na atualização seguinte;
   - [ ] O texto **Atualizado há X s** avança a cada segundo e volta para *"Atualizado agora"* a cada nova leitura;
   - [ ] Ao tirar a câmera da esteira, depois de ~1,5 s os painéis somem e volta a mensagem *"Aponte a câmera…"*;
   - [ ] Se negar a permissão da câmera, aparece *"Não foi possível acessar a câmera"*.

## Ajustes finos

Todas as configurações ficam em `js/config.js`:

| Constante | Padrão | O que faz |
|---|---|---|
| `INTERVALO_MS` | `3000` | Tempo entre atualizações dos dados (ms) |
| `TOLERANCIA_PERDA_MS` | `1500` | Tempo que os painéis continuam na tela depois de perder a esteira (ms) |
| `LIMITE_ALERTA_ALTO` | `80` | A partir deste nível (%), status **Atenção** (amarelo) |
| `LIMITE_CRITICO` | `95` | A partir deste nível (%), status **Crítico** (vermelho) |
| `LIMITE_ALERTA_BAIXO` | `10` | Abaixo deste nível (%), **Normal (nível baixo)**, apenas informativo |
| `SALTO_ENCHIMENTO_RAPIDO` | `5` | Aumento (pontos percentuais) entre duas atualizações que mostra a faixa de enchimento acima do padrão |

Na simulação, o nível sobe de 0,5 a 2 pontos por atualização, dá um salto maior que `SALTO_ENCHIMENTO_RAPIDO` a cada 8 a 10 atualizações e, ao passar de 97%, "esvazia" e recomeça perto de 20%. Assim a demonstração passa pelo verde, amarelo e vermelho e mostra a faixa de enchimento.

### A câmera não abre / tela preta

- Abra pelo link **HTTPS** do GitHub Pages.
- Confira se o navegador tem permissão de câmera (cadeado na barra de endereço → Permissões).
- Feche outros apps que estejam usando a câmera.
