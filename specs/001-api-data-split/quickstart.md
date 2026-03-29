# Quickstart: Backend Modularization Development Setup

**Feature Branch**: `001-api-data-split`

## Prerequisites

- Java 23+ (JDK)
- Maven 3.9+
- Docker & Docker Compose
- Node.js 18+ (for frontend)

## 1. Start PostgreSQL

From the repository root:

```bash
docker compose up -d
```

This starts PostgreSQL 16 on port `5432` with:
- Database: `nhl_stats`
- Username: `whoshot`
- Password: `whoshot`

Verify it's running:
```bash
docker compose ps
```

## 2. Build the Backend

```bash
cd backend
mvn clean install -DskipTests
```

This builds all three modules: `domain`, `data-job`, `api`.

## 3. Run the API Module

```bash
cd backend/api
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

## 4. Run the Data-Job Module

In a separate terminal:

```bash
cd backend/data-job
mvn spring-boot:run
```

The data-job connects to the NHL API, syncs data, and exits when done (non-game day) or runs continuous sync during game times.

## 5. Run the Frontend

In a separate terminal:

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on `http://localhost:3000` and connects to the API at `http://localhost:8080/api`.

## 6. Run Tests

```bash
# All backend tests
cd backend
mvn test

# Only API module tests
cd backend/api
mvn test

# Only data-job tests
cd backend/data-job
mvn test
```

Tests use Testcontainers — Docker must be running for repository/integration tests.

## Configuration

### PostgreSQL Connection (both modules)

```properties
# backend/api/src/main/resources/application.properties
# backend/data-job/src/main/resources/application.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/nhl_stats
spring.datasource.username=whoshot
spring.datasource.password=whoshot
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=update
```

### Frontend API URL

```env
# frontend/.env
VITE_API_BASE_URL=http://localhost:8080/api
```

## Ports

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL | 5432 | Database |
| API | 8080 | REST API for frontend |
| Frontend | 3000 | Vue dev server |

## Common Issues

- **"Connection refused" on port 5432**: Run `docker compose up -d` to start PostgreSQL.
- **"Table not found"**: First run uses `ddl-auto=update` to create tables. Run the data-job once to populate data.
- **"No data on frontend"**: Run the data-job module first to sync NHL data, then access the frontend.
- **Testcontainers failures**: Ensure Docker is running and has sufficient resources.
