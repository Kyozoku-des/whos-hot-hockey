# Multi-stage build for NHL Stats API
FROM maven:3.9-eclipse-temurin-23 AS build

WORKDIR /app

# Copy parent pom and all module poms first (for better layer caching)
COPY pom.xml ./
COPY domain/pom.xml ./domain/
COPY api/pom.xml ./api/
COPY data-job/pom.xml ./data-job/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl api -am

# Copy source code
COPY domain/src ./domain/src
COPY api/src ./api/src

# Build the application
RUN mvn clean package -pl api -am -DskipTests

# Runtime stage
FROM eclipse-temurin:23-jre-alpine

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/api/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
