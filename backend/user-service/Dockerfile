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
ENTRYPOINT ["java", "-jar", "app.jar"]
