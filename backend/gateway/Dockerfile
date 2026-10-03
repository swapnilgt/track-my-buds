# Build from the backend/ directory as context so the shared version catalog
# (../gradle/libs.versions.toml, referenced by settings.gradle.kts) is available:
#   docker build -f gateway/Dockerfile -t trackmybuds/gateway backend/
# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradle/ ./gradle/
COPY gateway/ ./gateway/
WORKDIR /workspace/gateway
RUN ./gradlew --no-daemon clean bootJar

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/gateway/build/libs/*.jar app.jar
EXPOSE 8080
# Runtime config via env (12-factor): set SPRING_PROFILES_ACTIVE and, for non-local
# profiles, USER_SERVICE_URI + ZIPKIN_ENDPOINT. Injected by compose/deploy.
ENTRYPOINT ["java", "-jar", "app.jar"]
