# Shared base: copy reactor metadata. Package downloads only the plugins and
# dependencies this build uses; go-offline also fetched unused legacy site tools.
FROM maven:3.9-eclipse-temurin-25-alpine AS base
RUN apk upgrade --no-cache
# Maven 3.9.16, the newest 3.9 release, bundles Guava 33.6.0, which has CVE-2026-102554
# (fixed in 33.7.2). Maven loads every jar in its lib directory and 33.7.2 needs the same
# failureaccess 1.0.3, so swap in the fixed release. The build fails once Maven bundles it,
# so remove this then.
ARG MAVEN_GUAVA=33.7.2-jre
ARG MAVEN_GUAVA_SHA256=b530942257fb935f8b2cfaa5f8eb5bd59c555fd8e8d01b8ce98912e077ea606c
RUN lib="$MAVEN_HOME/lib" \
 && set -- "$lib"/guava-*.jar \
 && if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then echo "expected one Guava jar in $lib" >&2; exit 1; fi \
 && bundled=$(basename "$1" .jar | sed 's/^guava-//') \
 && if ! java -cp "$(echo "$lib"/maven-artifact-*.jar)" \
        org.apache.maven.artifact.versioning.ComparableVersion "$bundled" "$MAVEN_GUAVA" \
        | grep -qF "$bundled < $MAVEN_GUAVA"; then \
      echo "Maven bundles Guava $bundled; remove the Guava swap" >&2; exit 1; fi \
 && wget -q -O "$lib/guava-$MAVEN_GUAVA.jar" \
      "https://repo1.maven.org/maven2/com/google/guava/guava/$MAVEN_GUAVA/guava-$MAVEN_GUAVA.jar" \
 && echo "$MAVEN_GUAVA_SHA256  $lib/guava-$MAVEN_GUAVA.jar" | sha256sum -c - \
 && rm "$1"

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
