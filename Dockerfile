# syntax=docker/dockerfile:1

FROM node:20-alpine AS frontend
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY tailwind.config.js postcss.config.js ./
COPY src/main/resources/static/css/input.css ./src/main/resources/static/css/input.css
COPY src/main/resources/templates ./src/main/resources/templates
RUN npm run build:css

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
COPY --from=frontend /app/src/main/resources/static/css/app.css ./src/main/resources/static/css/app.css
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S smartrent && adduser -S smartrent -G smartrent
COPY --from=build /app/target/glass-living.jar /app/glass-living.jar
RUN mkdir -p /app/uploads && chown -R smartrent:smartrent /app
USER smartrent
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/glass-living.jar"]
