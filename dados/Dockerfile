# Ciência de Dados do MetalTech: roda os scripts de dados/ sob demanda
# (não fica no ar). Exemplo:
#   docker compose run --rm dados analise_estatistica.py --fonte api --url http://backend:8080
FROM python:3.13-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    MPLBACKEND=Agg

WORKDIR /app/dados
COPY requirements.txt requirements-dev.txt ./
RUN pip install --no-cache-dir -r requirements-dev.txt

# Cópia dos scripts para a imagem funcionar sozinha. No compose.yaml a pasta dados/
# é montada por cima, para gráficos e resultados aparecerem direto no host.
COPY . .

ENTRYPOINT ["python"]
CMD ["analise_estatistica.py"]
