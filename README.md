# RA Esteira — MetalTech

Aplicação **WebAR** do Projeto Integrador da MetalTech (ADS). Ao apontar a câmera do celular para a **foto da esteira de triagem**, o app mostra por cima da imagem:

1. **Contagem de peças**: metálicas, não metálicas e total;
2. **Nível do reservatório**: porcentagem com barra e cor de alerta
   - 🟢 verde: abaixo de 70%
   - 🟡 amarelo: de 70% a 89%
   - 🔴 vermelho: 90% ou mais

Quando a imagem sai da câmera, os painéis somem e o app volta a mostrar *"Aponte a câmera para a imagem da esteira…"*.

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
│   └── target.mind   # imagem-alvo compilada (foto da esteira)
├── css/
│   └── style.css     # estilos dos painéis, selo e barra de nível
└── js/
    ├── config.js     # MODO_TESTE, API_URL, INTERVALO_MS
    ├── dados.js      # obterDados(): simulado ou fetch na API
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

## Como testar com a imagem da esteira

1. Abra o endereço do GitHub Pages no celular (Chrome no Android ou Safari no iPhone).
2. Permita o acesso à câmera.
3. Abra a **foto da esteira** (a mesma usada para gerar o `target.mind`) na tela do computador ou imprima.
4. Aponte a câmera do celular para a foto.
5. Confira:
   - [ ] O selo amarelo **⚠ DADOS DE TESTE (SIMULADOS)** aparece no topo;
   - [ ] Ao reconhecer a foto, aparecem os dois painéis (contagem e nível);
   - [ ] Os números mudam a cada 3 segundos;
   - [ ] A barra muda de cor (verde → amarelo → vermelho) conforme o nível;
   - [ ] Ao tirar a câmera da foto, os painéis somem e volta a mensagem *"Aponte a câmera…"*;
   - [ ] Se negar a permissão da câmera, aparece *"Não foi possível acessar a câmera"*.

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
