# Stage 1: build the Spring Boot jar
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY scheduleapp/ .
RUN chmod +x mvnw && ./mvnw package -DskipTests

# Stage 2: run the jar
FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
