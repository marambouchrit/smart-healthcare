# syntax=docker/dockerfile:1
# API Gateway (Spring Cloud Gateway) — image construite depuis la racine du dépôt, comme les autres composants (D40)
#   docker build -f gateway/Dockerfile -t smart-healthcare/gateway .

# 1) Construction : JDK + Maven Wrapper (cache Maven partagé entre les builds)
FROM eclipse-temurin:21.0.9_10-jdk-noble AS construction
WORKDIR /src
COPY gateway gateway
WORKDIR /src/gateway
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q package -DskipTests

# 2) Exécution : JRE seul, utilisateur sans privilèges
FROM eclipse-temurin:21.0.9_10-jre-noble
RUN useradd --system --uid 10001 --no-create-home appli
WORKDIR /app
COPY --from=construction /src/gateway/target/gateway-0.1.0.jar app.jar
USER appli
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
