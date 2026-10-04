# Who's Hot NHL - NHL Statistics Tracker

A full-stack application for tracking NHL statistics with a focus on identifying "hot" and "cold" players and teams based on recent performance.

## Tech Stack

- **Frontend**: Vue 3, Pinia, Vue Router, Chart.js, Vite
- **Backend**: Spring Boot 4.1.1 (Java 25), PostgreSQL 17
- **Infrastructure**: Podman/Docker Compose

## Quick Start

### Prerequisites

- Podman & podman-compose (or Docker & Docker Compose)
- Optionally: JDK 25, Maven 3.9+, Node.js 18+ (for manual development)

### Running with Podman Compose (Recommended)

See [environment setup](backend/ENVIRONMENTS.md) for `.env` templates, local log
retention, isolated worktrees, and production configuration. Compose is local-only.

Start all services (PostgreSQL, backend API, data sync, and frontend):

```bash
podman-compose up -d
```

Or with Docker:

```bash
docker compose up -d
```

After pulling a backend upgrade (for example the Java 25 / Spring Boot 4.1.1 migration), rebuild the
images so the containers run the new Temurin 25 runtime: `podman-compose build` (or `docker compose build`).

The application will be available at:
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

### Initial Data Load

To populate the database with current NHL season data:

```bash
podman-compose stop data-job
podman-compose run --rm -e SPRING_PROFILES_ACTIVE=local,initial-load data-job
podman-compose up -d data-job
```

This runs a one-shot load of team standings, team schedules, player statistics and game logs,
then resumes the daemon.

### Stopping Services

```bash
podman-compose down
```

## Project Structure

```
whos-hot-nhl/
├── backend/
│   ├── domain/              # Shared JPA entities & repositories
│   ├── api/                 # REST API (port 8080)
│   └── data-job/            # NHL data sync daemon
├── frontend/                # Vue 3 SPA (port 3000)
├── specs/                   # Feature specifications
└── compose.yaml             # All services configuration
```

## Development

### Manual Development Setup

If you prefer to run services individually:

#### 1. Start PostgreSQL only

```bash
podman-compose up -d postgres
```

#### 2. Run the backend applications

Follow [local Java process setup](backend/ENVIRONMENTS.md#local-java-processes)
for building and running the API, data sync daemon, and one-off loads.

#### 3. Run Frontend

```bash
cd frontend
npm install
npm run dev
```

### Running Tests

```bash
cd backend
mvn test
```

## Features

- **Team Standings**: View NHL team standings with streaks and recent form
- **Player Statistics**: Comprehensive player stats including points, goals, assists
- **Point Streaks**: Track players with active point streaks
- **Hot Players**: Identify players performing exceptionally well in last 10 games
- **Win/Loss Streaks**: Monitor teams on winning or losing streaks
- **Individual Pages**: Detailed game-by-game statistics for players and teams
- **Search**: Real-time search for players and teams

## Documentation

- [Backend README](backend/README.md) - Detailed backend documentation
- [API Reference](backend/API_REFERENCE.md) - Complete API endpoint documentation
- [Quickstart Guide](specs/001-api-data-split/quickstart.md) - Development setup guide

## Using Docker Instead of Podman

All commands work identically with Docker:

```bash
# Replace podman-compose with docker compose
docker compose up -d
docker compose down
docker compose ps
```

## License

This project is for educational purposes.
