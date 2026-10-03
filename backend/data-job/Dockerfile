# Multi-stage build for NHL Data Sync Job
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

# Copy parent pom and all module poms first (for better layer caching)
COPY pom.xml ./
COPY domain/pom.xml ./domain/
COPY data-job/pom.xml ./data-job/
COPY api/pom.xml ./api/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl data-job -am

# Copy source code
COPY domain/src ./domain/src
COPY data-job/src ./data-job/src

# Build the application
RUN mvn clean package -pl data-job -am -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
RUN addgroup -g 1000 app && adduser -D -u 1000 -G app app \
    && mkdir -p /app/logs && chown app:app /app/logs
USER 1000:1000

# Copy the built JAR from build stage
COPY --from=build /app/data-job/target/*.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
