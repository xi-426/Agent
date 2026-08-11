FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN mkdir -p /app/data/uploads && chown -R 10001:0 /app

COPY --from=build --chown=10001:0 \
    /workspace/target/zhiyu-agent-0.0.1-SNAPSHOT.jar \
    /app/app.jar

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
