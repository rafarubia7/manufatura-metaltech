# Dashboard do MetalTech (React + Vite), em duas etapas:
# a primeira gera os arquivos estáticos e a segunda os serve com Nginx.

# ---------- Etapa 1: build ----------
# O Vite 8 exige Node 20.19+ ou 22.12+
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# Sem VITE_API_URL, o dashboard chama a API no mesmo host em que a página foi
# aberta, na porta 8080 (a porta publicada pelo serviço backend no compose.yaml).
ARG VITE_API_URL=""
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# ---------- Etapa 2: servidor web ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
