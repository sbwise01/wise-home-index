# syntax=docker/dockerfile:1

# ---- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first to leverage Docker layer caching.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package

# ---- Runtime stage -----------------------------------------------------------
FROM tomcat:10.1-jre17-temurin
LABEL org.opencontainers.image.title="wise-home-index" \
      org.opencontainers.image.description="Index page for services hosted on the wise-k8s homelab cluster"

# Remove the bundled sample apps so only our application is served.
RUN rm -rf /usr/local/tomcat/webapps/*

# Deploy as ROOT so the index page is served from "/".
COPY --from=build /workspace/target/wise-home-index.war /usr/local/tomcat/webapps/ROOT.war

# Applications are discovered from Kubernetes Ingress resources at runtime using
# the in-cluster service account (see the RBAC in the wise-k8s deployment). No
# static configuration file is required.

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/health || exit 1

CMD ["catalina.sh", "run"]
