FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src

RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre

RUN groupadd --system fieldops && useradd --system --gid fieldops fieldops
WORKDIR /app

COPY --from=build /workspace/target/fieldops-api-*.jar /app/fieldops-api.jar

RUN mkdir -p /app/data/evidence && chown -R fieldops:fieldops /app
USER fieldops

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/fieldops-api.jar"]
