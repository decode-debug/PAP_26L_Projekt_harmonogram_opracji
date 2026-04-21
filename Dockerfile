# Stage 1: build the React frontend
FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY scheduleapp/frontend/package*.json ./
RUN npm ci
COPY scheduleapp/frontend/ .
RUN npm run build

# Stage 2: build the Spring Boot jar (with frontend bundled inside)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY scheduleapp/ .
# Replace old static HTML with the compiled React app
RUN rm -rf src/main/resources/static
COPY --from=frontend-build /frontend/dist src/main/resources/static
RUN chmod +x mvnw && ./mvnw package -DskipTests

# Stage 3: run the jar
FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
