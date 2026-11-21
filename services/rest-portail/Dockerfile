# syntax=docker/dockerfile:1
# Service REST — Portail patient & rendez-vous — image construite depuis la racine du dépôt (le code est généré depuis contracts/, D40)
#   docker build -f services/rest-portail/Dockerfile -t smart-healthcare/rest-portail .

# 1) Construction : JDK + Maven Wrapper (cache Maven partagé entre les builds)
FROM eclipse-temurin:21.0.9_10-jdk-noble AS construction
WORKDIR /src
COPY contracts contracts
COPY services/rest-portail services/rest-portail
WORKDIR /src/services/rest-portail
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q package -DskipTests

# 2) Exécution : JRE seul, utilisateur sans privilèges
FROM eclipse-temurin:21.0.9_10-jre-noble
RUN useradd --system --uid 10001 --no-create-home appli
WORKDIR /app
COPY --from=construction /src/services/rest-portail/target/rest-portail-0.1.0.jar app.jar
USER appli
EXPOSE 8082
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
