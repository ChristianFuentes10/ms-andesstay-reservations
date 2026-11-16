# ---- Etapa 1: compilacion ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# ---- Etapa 2: imagen de ejecucion ----
FROM eclipse-temurin:21-jre AS runtime
LABEL maintainer="AndesStay Equipo DSY1107"
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
WORKDIR /app
COPY --from=builder /build/target/ms-andesstay-reservations-*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
