FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q clean package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-noto-cjk curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --home-dir /app app \
    && mkdir -p /data/uploads /data/logs /app/certs \
    && curl --fail --silent --show-error --location \
       https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
       --output /app/certs/rds-global-bundle.pem \
    && chown -R app:app /data /app/certs
WORKDIR /app
COPY --from=build /workspace/target/smart-platform-*.jar app.jar
COPY --chmod=755 scripts/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl --fail --silent --show-error http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
