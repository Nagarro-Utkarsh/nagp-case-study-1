FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl integration-service -am -DskipTests package
FROM eclipse-temurin:21-jre
COPY --from=build /workspace/integration-service/target/integration-service-1.0.0.jar /app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app.jar"]
