# Build with the same Java major version as the application. The patch-pinned
# tags make rebuilds reproducible while still receiving image security updates
# when this file is intentionally changed.
FROM maven:3.9-eclipse-temurin-26-alpine AS builder

RUN addgroup -S -g 10001 buildgroup \
    && adduser -S -D -u 10001 -G buildgroup builduser
WORKDIR /workspace
RUN chown builduser:buildgroup /workspace
USER builduser

COPY --chown=builduser:buildgroup pom.xml ./

COPY --chown=builduser:buildgroup src ./src
RUN mvn -B -ntp clean package -DskipTests


# Run as a dedicated UID. The application writes only its rolling log under
# /app/logs; secrets are injected by Compose/secret-manager environment vars.
FROM eclipse-temurin:21.0.8_9-jre-alpine

RUN addgroup -S -g 10001 appgroup \
    && adduser -S -D -u 10001 -G appgroup appuser \
    && mkdir -p /app/logs \
    && chown -R appuser:appgroup /app
WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /workspace/target/*.jar /app/app.jar

USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
