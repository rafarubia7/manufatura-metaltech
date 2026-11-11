# RA Esteira — MetalTech

Aplicação **WebAR** do Projeto Integrador da MetalTech (ADS). Ao apontar a câmera do celular para a **esteira de triagem real**, o app reconhece a esteira e mostra por cima dela:

1. **Contagem de peças**: metálicas, não metálicas e total;
2. **Nível do reservatório**: porcentagem com barra e cor de alerta
   - 🟢 verde: abaixo de 70%
   - 🟡 amarelo: de 70% a 89%
   - 🔴 vermelho: 90% ou mais

Quando a esteira sai da câmera, os painéis ficam mais 1,5 s na tela (para não piscarem) e depois somem; o app volta a mostrar *"Aponte a câmera para a esteira…"*.

Os painéis podem ser **arrastados com o dedo** para qualquer lugar da tela. A posição fica salva no celular, e o botão **↺ Organizar** volta tudo para o lugar inicial.

O reconhecimento usa o MindAR, que compara a câmera com **fotos da esteira** compiladas no `assets/target.mind`. Por isso a foto precisa ter sido tirada da posição de onde o usuário vai apontar o celular (veja [Como fazer o app reconhecer a esteira real](#como-fazer-o-app-reconhecer-a-esteira-real)).

> ⚠ **Atenção:** por enquanto o app usa **DADOS DE TESTE (SIMULADOS)**. Os números são inventados e mudam a cada 3 segundos. Veja [Como trocar os dados de teste pela API real](#como-trocar-os-dados-de-teste-pela-api-real).

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
    ├── config.js     # MODO_TESTE, API_URL, INTERVALO_MS, TOLERANCIA_PERDA_MS
    ├── dados.js      # obterDados(): simulado ou fetch na API
    ├── arrastar.js   # arrastar os painéis e lembrar a posição
    └── main.js       # eventos targetFound/targetLost/arError e atualização da tela
```

## Como rodar localmente

A câmera só funciona em **HTTPS** ou em **localhost**. Abrir o `index.html` direto (`file://`) não funciona.

```bash
npx serve .
```

Depois acesse `http://localhost:3000` no navegador do computador (com webcam).

Para testar **no celular** pela rede local é preciso HTTPS. O jeito mais simples é publicar no GitHub Pages (abaixo). Outra opção é um túnel HTTPS, por exemplo:

```bash
npx serve . -l 3000
npx localtunnel --port 3000
```

## Como publicar no GitHub Pages

1. Envie o projeto para o GitHub (`git push`).
2. No repositório, vá em **Settings → Pages**.
3. Em **Source**, escolha **Deploy from a branch**, branch `main`, pasta `/ (root)` e clique em **Save**.
4. Aguarde 1–2 minutos. O endereço será algo como `https://SEU-USUARIO.github.io/ra-manotech/`.

## Como testar na esteira

1. Abra o endereço do GitHub Pages no celular (Chrome no Android ou Safari no iPhone).
2. Permita o acesso à câmera.
3. Fique na mesma posição de onde a foto da esteira foi tirada e aponte o celular para a **esteira**, enquadrando-a como na foto.
   - Sem acesso à esteira? Dá para testar apontando para a própria foto na tela do computador.
4. Confira:
   - [ ] O selo amarelo **⚠ DADOS DE TESTE (SIMULADOS)** aparece no topo;
   - [ ] Ao reconhecer a esteira, aparecem os dois painéis (contagem e nível);
   - [ ] Os números mudam a cada 3 segundos;
   - [ ] Dá para arrastar cada painel com o dedo; ao recarregar a página ele continua no lugar escolhido;
   - [ ] O botão **↺ Organizar** volta os painéis para a posição inicial;
   - [ ] A barra muda de cor (verde → amarelo → vermelho) conforme o nível;
   - [ ] Ao tirar a câmera da esteira, depois de ~1,5 s os painéis somem e volta a mensagem *"Aponte a câmera…"*;
   - [ ] Se negar a permissão da câmera, aparece *"Não foi possível acessar a câmera"*.

### A câmera não abre / tela preta

- Abra pelo link **HTTPS** do GitHub Pages. Abrir o arquivo direto (`file://`) ou por `http://IP-do-PC` não libera a câmera (o app avisa na tela).
- Confira se o navegador tem permissão de câmera (cadeado na barra de endereço → Permissões).
- Feche outros apps que estejam usando a câmera.
- Depois de publicar uma nova versão, espere 1–2 min e **feche e abra a aba de novo**. O GitHub Pages deixa os arquivos em cache por até 10 min.
- Para saber se a versão nova carregou: a mensagem inicial deve ser *"Aponte a câmera para a esteira…"* (a versão antiga dizia *"…para a imagem da esteira…"* e tinha fundo preto).
- Ao alterar CSS ou JS, aumente o número `?v=` nos links do `index.html` (ex.: `style.css?v=4` → `?v=5`) para forçar o celular a baixar os arquivos novos.

## Como fazer o app reconhecer a esteira real

O MindAR reconhece **imagens**, não objetos 3D. Ele funciona na esteira real comparando a câmera com uma foto dela, então a qualidade da foto é o que decide se o reconhecimento funciona:

1. Tire a foto **do lugar onde o usuário vai ficar**, na altura do celular, com boa luz.
2. Enquadre só a esteira (corte fundo, chão e paredes lisas).
3. Prefira partes com bastante detalhe e contraste (estrutura, sensores, etiquetas, parafusos). Superfícies lisas e reflexivas atrapalham.
4. Para funcionar de mais de um ângulo, tire **várias fotos** (frente, lateral, de cima).
5. Compile as fotos em <https://hiukim.github.io/mind-ar-js-doc/tools/compile> (pode enviar várias de uma vez), baixe o arquivo e substitua `assets/target.mind`.
6. Se compilou mais de uma foto, adicione uma entidade por foto no `index.html`:

   ```html
   <a-entity class="alvo" mindar-image-target="targetIndex: 0"></a-entity>
   <a-entity class="alvo" mindar-image-target="targetIndex: 1"></a-entity>
   <a-entity class="alvo" mindar-image-target="targetIndex: 2"></a-entity>
   ```

   Qualquer uma delas, ao ser reconhecida, mostra os painéis.

Ajustes finos (no `index.html` e no `js/config.js`):

- `warmupTolerance` (atributo `mindar-image` da cena): quanto menor, mais rápido reconhece.
- `missTolerance` (atributo `mindar-image` da cena): quanto maior, menos "perde" a esteira quando o celular treme.
- `TOLERANCIA_PERDA_MS` (`js/config.js`): por quanto tempo os painéis ficam na tela depois de perder a esteira.

## Como trocar os dados de teste pela API real

Toda a troca acontece em dois arquivos:

1. Em **`js/config.js`**:

   ```js
   const MODO_TESTE = false;                          // desliga a simulação
   const API_URL = "https://endereco-da-api/esteira"; // endereço real
   const INTERVALO_MS = 3000;                         // de quanto em quanto tempo buscar
   ```

2. A API deve responder (GET) com um JSON neste formato:

   ```json
   { "metalicas": 42, "naoMetalicas": 17, "nivelReservatorio": 78.5 }
   ```

   Se o formato da API for diferente, ajuste **somente** a função `obterDados()` em **`js/dados.js`** para converter a resposta para esse formato. O resto do app não precisa mudar.

Observações:

- Se a API falhar ou estiver fora do ar, o app mostra **"Sem conexão com a API"**.
- A API precisa estar em **HTTPS** (o GitHub Pages bloqueia chamadas HTTP) e liberar **CORS** para o domínio do GitHub Pages.
- Com `MODO_TESTE = false`, o selo de dados de teste some automaticamente.
