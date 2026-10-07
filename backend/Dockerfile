# Build stage: compile and package. Tests are not run here — they need Docker themselves
# (Testcontainers), so they run with ./gradlew test on the host or in CI.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
# Wrapper and build files first, so the dependency layer is cached across source changes.
COPY gradlew .
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew --no-daemon -x test bootJar

# Run stage: JRE only, non-root.
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 carelink
USER carelink
WORKDIR /app
COPY --from=build /src/build/libs/backend-*.jar app.jar
EXPOSE 8080
# The container, not the host, is the memory boundary: cap the heap as a share of the cgroup limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "/app/app.jar"]
