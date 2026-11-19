FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl activity-service -am -DskipTests package
FROM eclipse-temurin:21-jre
COPY --from=build /workspace/activity-service/target/activity-service-1.0.0.jar /app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app.jar"]
