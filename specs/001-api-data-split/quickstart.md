# Quickstart: Backend Modularization Development Setup

**Feature Branch**: `001-api-data-split`

## Prerequisites

- Java 23+ (JDK)
- Maven 3.9+
- Podman & podman-compose (or Docker & Docker Compose)
- Node.js 18+ (for frontend)

## 1. Start All Services (Recommended)

From the repository root:

```bash
podman-compose up -d
```

Or with Docker:
```bash
docker compose up -d
```

This starts:
- **PostgreSQL 16** on port `5432` (db: `nhl_stats`, user: `whoshot`, password: `whoshot`)
- **Backend API** on port `8080`
- **Data-job** (NHL data sync daemon)
- **Frontend** on port `3000`

Verify services are running:
```bash
podman-compose ps
# or
docker compose ps
```

**For initial data load**, set the environment variable before starting:
```bash
DATA_JOB_PROFILE=initial-load podman-compose up -d
```

## Alternative: Manual Development Setup

If you prefer to run services manually for development, start only PostgreSQL:

```bash
podman-compose up -d postgres
```

## 2. Build the Backend (Manual Setup Only)

```bash
cd backend
mvn clean install -DskipTests
```

This builds all three modules: `domain`, `data-job`, `api`.

## 3. Run the API Module (Manual Setup Only)

```bash
cd backend/api
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

## 4. Run the Data-Job Module (Manual Setup Only)

In a separate terminal:

```bash
cd backend/data-job
mvn spring-boot:run
```

The data-job connects to the NHL API, syncs data, and exits when done (non-game day) or runs continuous sync during game times.

## 5. Run the Frontend (Manual Setup Only)

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
