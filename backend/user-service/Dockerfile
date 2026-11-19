FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl user-service -am -DskipTests package
FROM eclipse-temurin:21-jre
COPY --from=build /workspace/user-service/target/user-service-1.0.0.jar /app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]
