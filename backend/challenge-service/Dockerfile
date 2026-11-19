FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl challenge-service -am -DskipTests package
FROM eclipse-temurin:21-jre
COPY --from=build /workspace/challenge-service/target/challenge-service-1.0.0.jar /app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app.jar"]
