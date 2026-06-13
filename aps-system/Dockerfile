FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY maven-settings.xml ./
RUN mvn -s maven-settings.xml -q -DskipTests dependency:go-offline

COPY src src
RUN mvn -s maven-settings.xml -q -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
RUN useradd --system --create-home --shell /usr/sbin/nologin aps

COPY --from=build /workspace/target/aps-system-0.0.1-SNAPSHOT.jar /app/app.jar
USER aps

EXPOSE 8091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
