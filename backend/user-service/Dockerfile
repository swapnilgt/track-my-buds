# NOTE: build from the backend/ directory as context (so ../gradle/libs.versions.toml
# is available), or copy the catalog into the service on repo extraction.
# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon clean bootJar

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
