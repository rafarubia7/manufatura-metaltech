FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copiar arquivos de build
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY src ./src

# Build da aplicação
RUN ./mvnw clean package -DskipTests

# Imagem final
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar JAR buildado
COPY --from=build /app/target/*.jar app.jar

# Expor porta
EXPOSE 8080

# Entry point
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
