# RA Esteira — MetalTech

Aplicação **WebAR** do Projeto Integrador da MetalTech. Ao apontar a câmera do celular para a **esteira de triagem real**, o app reconhece a esteira e mostra por cima dela:

1. **Contagem de peças**: metálicas, não metálicas e total;
2. **Nível do reservatório**: porcentagem com barra e cor de alerta
   - 🟢 verde: abaixo de 70%
   - 🟡 amarelo: de 70% a 89%
   - 🔴 vermelho: 90% ou mais

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
ra-manotech/
├── index.html        # cena de RA + painéis sobrepostos
├── README.md
├── assets/
│   └── target.mind   # foto(s) da esteira compiladas para o MindAR
├── css/
│   └── style.css     # estilos dos painéis, selo e barra de nível
└── js/
    ├── config.js     # INTERVALO_MS, TOLERANCIA_PERDA_MS e demais configurações
    ├── dados.js      # obterDados(): gera os dados fictícios (simulados)
    ├── arrastar.js   # mover/redimensionar os painéis e lembrar posição e tamanho
    └── main.js       # eventos targetFound/targetLost/arError e atualização da tela
```

## Como testar na esteira

1. Abra o endereço do GitHub Pages no celular (Chrome no Android ou Safari no iPhone).
2. Permita o acesso à câmera.
3. Fique na mesma posição de onde a foto da esteira foi tirada e aponte o celular para a **esteira**, enquadrando-a como na foto.
   - Sem acesso à esteira? Dá para testar apontando para a própria foto na tela do computador.
4. Confira:
   - [ ] O selo amarelo **⚠ DADOS SIMULADOS** aparece no topo;
   - [ ] Os valores exibidos são fictícios (simulados), conforme o aviso no topo deste README;
   - [ ] Ao reconhecer a esteira, aparecem os dois painéis (contagem e nível);
   - [ ] Os números mudam a cada 3 segundos;
   - [ ] Dá para arrastar cada painel com o dedo;
   - [ ] Os botões **−** / **+** e a pinça com dois dedos diminuem/aumentam o painel;
   - [ ] Ao recarregar a página, os painéis continuam no lugar e no tamanho escolhidos;
   - [ ] O botão **↺ Organizar** volta os painéis para a posição e o tamanho iniciais;
   - [ ] A barra muda de cor (verde → amarelo → vermelho) conforme o nível;
   - [ ] Ao tirar a câmera da esteira, depois de ~1,5 s os painéis somem e volta a mensagem *"Aponte a câmera…"*;
   - [ ] Se negar a permissão da câmera, aparece *"Não foi possível acessar a câmera"*.

### A câmera não abre / tela preta

- Abra pelo link **HTTPS** do GitHub Pages.
- Confira se o navegador tem permissão de câmera (cadeado na barra de endereço → Permissões).
- Feche outros apps que estejam usando a câmera.
