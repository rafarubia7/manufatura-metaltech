# API REST do MetalTech (Java 17 / Spring Boot), em duas etapas:
# a primeira compila com Maven e a segunda roda só o .jar numa imagem leve (JRE).

# ---------- Etapa 1: build ----------
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Wrapper do Maven. O sed remove CRLF: no Windows (core.autocrlf=true) o mvnw
# chega com quebra de linha CRLF e o /bin/sh do Alpine não consegue executá-lo.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Baixa as dependências antes do código, para reaproveitar o cache do Docker
# quando só o código-fonte muda.
RUN ./mvnw -B -q dependency:go-offline

COPY src ./src
# O pom.xml não herda do spring-boot-starter-parent, então o "package" sozinho gera
# um .jar comum (sem dependências nem Main-Class). O spring-boot:repackage
# transforma esse .jar no executável "fat jar" que roda com java -jar.
RUN ./mvnw -B -q clean package spring-boot:repackage -DskipTests

# ---------- Etapa 2: execução ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Usuário sem privilégios de root
RUN addgroup -S metaltech && adduser -S metaltech -G metaltech
COPY --from=build /app/target/*.jar app.jar
USER metaltech

# Escuta em todas as interfaces para responder a requisições da rede do contêiner.
ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
