# syntax=docker/dockerfile:1

# ---- Stage 1: build React SPA (self-hosted fonts, no CDN) ----
FROM node:22-alpine AS frontend
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts
COPY frontend/ ./
# vite outDir = ../src/main/resources/static → /src/main/resources/static here
RUN npm run build

# ---- Stage 2: build Spring Boot fat JAR ----
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Fresh SPA from stage 1 replaces anything stale (dockerignore also excludes it)
COPY --from=frontend /src/main/resources/static ./src/main/resources/static
RUN mvn -B -q package -DskipTests

# ---- Stage 3: runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /build/target/marginwatch-1.0.0.jar /app/app.jar
EXPOSE 8080
CMD ["sh", "-c", "java -jar /app/app.jar"]
