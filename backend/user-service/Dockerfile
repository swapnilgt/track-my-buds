# Build from the backend/ directory as context so the shared version catalog
# (../gradle/libs.versions.toml, referenced by settings.gradle.kts) is available:
#   docker build -f user-service/Dockerfile -t trackmybuds/user-service backend/
# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradle/ ./gradle/
COPY user-service/ ./user-service/
WORKDIR /workspace/user-service
RUN ./gradlew --no-daemon clean bootJar

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/user-service/build/libs/*.jar app.jar
EXPOSE 8081
# Runtime config is supplied by the environment (12-factor). Set SPRING_PROFILES_ACTIVE
# (local|preprod|prod); for non-local profiles also DB_URL / DB_USERNAME / DB_PASSWORD
# and ZIPKIN_ENDPOINT. With no profile the base config has no datasource and startup
# fails — in compose/deploy the orchestration layer injects these.
ENTRYPOINT ["java", "-jar", "app.jar"]
