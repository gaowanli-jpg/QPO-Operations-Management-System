FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY maven-settings.xml .
RUN mvn -s maven-settings.xml -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -s maven-settings.xml -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
