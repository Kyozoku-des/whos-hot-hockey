# Shared base: copy reactor metadata. Package downloads only the plugins and
# dependencies this build uses; go-offline also fetched unused legacy site tools.
FROM maven:3.9-eclipse-temurin-25-alpine AS base
RUN apk upgrade --no-cache

WORKDIR /app

COPY pom.xml ./
COPY domain/pom.xml ./domain/
COPY api/pom.xml ./api/
COPY data-job/pom.xml ./data-job/

# Copy shared domain source (needed by both modules)
COPY domain/src ./domain/src

# ---- API build ----
FROM base AS api-build

COPY api/src ./api/src
RUN mvn clean package -pl api -am -DskipTests

# ---- Data-job build ----
FROM base AS datajob-build

COPY data-job/src ./data-job/src
RUN mvn clean package -pl data-job -am -DskipTests

# ---- API runtime ----
FROM eclipse-temurin:25-jre-alpine AS api

WORKDIR /app
# Take Alpine security fixes released since the base image was built.
RUN apk upgrade --no-cache \
    && addgroup -g 1000 app && adduser -D -u 1000 -G app app \
    && mkdir -p /app/logs && chown app:app /app/logs
USER 1000:1000
COPY --from=api-build /app/api/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# ---- Data-job runtime ----
FROM eclipse-temurin:25-jre-alpine AS data-job

WORKDIR /app
# Take Alpine security fixes released since the base image was built.
RUN apk upgrade --no-cache \
    && addgroup -g 1000 app && adduser -D -u 1000 -G app app \
    && mkdir -p /app/logs && chown app:app /app/logs
USER 1000:1000
COPY --from=datajob-build /app/data-job/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
