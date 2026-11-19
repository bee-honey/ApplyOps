# Build context: repository root (needs gradlew, settings.gradle.kts, schemas/).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY orchestrator/build.gradle.kts ./orchestrator/
RUN ./gradlew --no-daemon :orchestrator:dependencies > /dev/null
COPY orchestrator/src ./orchestrator/src
RUN ./gradlew --no-daemon :orchestrator:bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /src/orchestrator/build/libs/*.jar /app/orchestrator.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/orchestrator.jar"]
