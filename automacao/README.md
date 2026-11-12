# MetalTech Gateway

Gateway IO-Link para leitura de sensores e envio de leituras para a API MetalTech.

## Visão geral

Este projeto lê sensores conectados a um mestre IO-Link e envia payloads para a API definida em `config.json`.

O script principal é:

- `gateway.py` — executa a leitura dos sensores, classifica a peça e publica os dados

Arquivos do projeto:

- `config.example.json` — exemplo de configuração
- `config.json` — arquivo real usado em execução
- `requirements.txt` — dependências do projeto

## Requisitos

Instale as dependências:

```powershell
pip install -r requirements.txt
```

## Configuração

Crie o arquivo de configuração real a partir do exemplo:

```powershell
Copy-Item .\config.example.json .\config.json
```

Edite `config.json` com os valores corretos do seu ambiente, especialmente:

- `mestre_url`
- `api_url`
- `portas`
- `sensores`
- `distancia.vazio_raw`
- `distancia.cheio_raw`

## Scripts

### `gateway.py`

Executa o gateway principal.

Uso:

```powershell
python .\gateway.py --sondar # USA ESSE PELO AMOR DE DEUS
python .\gateway.py --dry-run # ESSE NAO PQ ELE APARECE COMO 1 SENSOR SO, SENDO 3
python .\gateway.py
```

### `--sondar`

Lê cada porta do mestre IO-Link e imprime o valor bruto em hexadecimal e decimal.

```powershell
python .\gateway.py --sondar
```

Útil para validar:

- se o mestre está acessível
- se as portas estão respondendo
- se a leitura bruta dos sensores está correta

### `--dry-run`

Executa o fluxo completo, mas em vez de enviar para a API imprime o JSON que seria enviado.

```powershell
python .\gateway.py --dry-run
```

### Execução normal

```powershell
python .\gateway.py
```

## Estrutura de configuração

Exemplo de configuração mínima:

```json
{
  "mestre_url": "http://10.110.12.41",
  "portas": {
    "distancia": 1,
    "indutivo": 3,
    "capacitivo": 2
  },
  "sensores": {
    "capacitivo": {
      "liga_acima": 1500,
      "desliga_abaixo": 1000
    },
    "indutivo": {
      "liga_abaixo": 20000,
      "desliga_acima": 25000
    }
  },
  "distancia": {
    "vazio_raw": 80,
    "cheio_raw": 5,
    "cm_por_unidade": 0.1,
    "amostras_mediana": 5
  },
  "api_url": "http://localhost:8080/api/v1/leituras"
}
```

## Diagnóstico e solução de problemas

### Erro: `FileNotFoundError: [Errno 2] No such file or directory: 'config.json'`

Crie o arquivo:

```powershell
Copy-Item .\config.example.json .\config.json
```

### Erro: `config: preencha distancia.vazio_raw e distancia.cheio_raw`

Preencha os valores de leitura bruta do sensor de distância em estado vazio e cheio.

### Porta com erro no `--sondar`

Verifique:

- se o sensor está realmente conectado à porta correta
- se o dispositivo na porta está funcionando
- se a porta corresponde ao sensor esperado
- se o mestre responde com o payload correto

## Observações

- O `--sondar` é útil para diagnosticar o hardware e validar a comunicação.
- O `--dry-run` é útil para testar a lógica sem enviar dados para a API.
- O modo normal executa a leitura em loop e publica as leituras.

## Licença

Projeto interno para uso do ambiente MetalTech.
