# syntax=docker/dockerfile:1
# Service SOAP — Dossier patient & facturation CNAM — image construite depuis la racine du dépôt (le code est généré depuis contracts/, D40)
#   docker build -f services/soap-dossier/Dockerfile -t smart-healthcare/soap-dossier .

# 1) Construction : JDK + Maven Wrapper (cache Maven partagé entre les builds)
FROM eclipse-temurin:21.0.9_10-jdk-noble AS construction
WORKDIR /src
COPY contracts contracts
COPY services/soap-dossier services/soap-dossier
WORKDIR /src/services/soap-dossier
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q package -DskipTests

# 2) Exécution : JRE seul, utilisateur sans privilèges
FROM eclipse-temurin:21.0.9_10-jre-noble
RUN useradd --system --uid 10001 --no-create-home appli
WORKDIR /app
COPY --from=construction /src/services/soap-dossier/target/soap-dossier-0.1.0.jar app.jar
USER appli
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
