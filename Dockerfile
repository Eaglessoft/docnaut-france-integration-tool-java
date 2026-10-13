FROM maven:3.9.14-eclipse-temurin-25 AS build
WORKDIR /source
COPY pom.xml ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM build AS verification
RUN mvn --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /source/target/docnaut-france-integration-tool-java-0.1.0.jar /app/app.jar
COPY LICENSE THIRD-PARTY-NOTICES.md /app/
ENV SERVER_PORT=8080
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
