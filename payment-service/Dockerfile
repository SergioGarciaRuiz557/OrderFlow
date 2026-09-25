FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradle ./gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY src ./src
# Normaliza el wrapper de los checkouts de Windows antes de ejecutarlo en Linux.
RUN sed -i 's/\r$//' gradlew \
    && sh gradlew --no-daemon --max-workers=2 bootJar \
    && cp build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S orderflow && adduser -S orderflow -G orderflow
COPY --from=build --chown=orderflow:orderflow /workspace/app.jar ./app.jar
# Reserva la mitad del límite de memoria para el heap y deja margen a la JVM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"
USER orderflow
ENTRYPOINT ["java", "-jar", "app.jar"]
