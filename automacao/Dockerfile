# Gateway IO-Link -> API MetalTech. Fica no ar o tempo todo lendo os sensores
# do mestre IO-Link (rede local) e enviando um POST por peça para a API.
FROM python:3.13-slim

# Logs aparecem na hora no "docker compose logs" (sem buffer)
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY gateway.py .

# Usuário sem privilégios de root
RUN useradd --system --no-create-home gateway
USER gateway

# O config.json (IP do mestre, portas, calibração) é montado pelo compose.yaml em
# /config/config.json, para ajustar sem reconstruir a imagem.
ENTRYPOINT ["python", "gateway.py", "--config", "/config/config.json"]
