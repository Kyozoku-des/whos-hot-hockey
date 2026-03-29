# Who's Hot - NHL Statistics Backend

A Spring Boot backend that provides REST APIs for NHL statistics with a focus on identifying "hot" and "cold" players and teams based on recent performance.

## Features

- **Team Standings**: View current NHL team standings
- **Player Statistics**: Access comprehensive player stats including points, goals, assists
- **Point Streaks**: Track players with active point streaks
- **Hot Players**: Identify players performing exceptionally well in recent games
- **Win/Loss Streaks**: Monitor teams on winning or losing streaks
- **Individual Pages**: Detailed statistics for specific players and teams

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Database**: PostgreSQL 16 (via Docker Compose)
- **Build Tool**: Maven
- **Java Version**: 21
- **Documentation**: SpringDoc OpenAPI (Swagger UI)

## Module Structure

The backend is organized as a Maven multi-module project with two independent Spring Boot applications:

- **domain** - Shared JPA entities, repositories, and common utilities used by both applications
- **api** - REST API application serving all `/api/**` endpoints (read-only, port 8080)
- **data-job** - NHL data sync daemon that fetches data from the NHL API and writes to PostgreSQL

## Prerequisites

- Java 21 or higher
- Maven 3.9+
- Docker & Docker Compose

## Getting Started

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Initial data load

Run the data-job with the `initial-load` profile to perform a full sync from the NHL API:

```bash
cd data-job
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=initial-load"
```

### 3. Run the data-job (ongoing sync)

```bash
cd data-job
mvn spring-boot:run
```

### 4. Run the API

```bash
cd api
mvn spring-boot:run
```

The API will start on `http://localhost:8080`.

## API Endpoints

### Team Endpoints

- `GET /api/teams/standings?season=` - Get team standings ordered by points
- `GET /api/teams/win-streaks?season=` - Get teams with active win streaks
- `GET /api/teams/loss-streaks?season=` - Get teams with active loss streaks
- `GET /api/teams/{teamCode}?season=` - Get specific team details with roster
- `GET /api/teams/{teamCode}/game-log?season=` - Get team game results

### Player Endpoints

- `GET /api/players/standings?season=` - Get player standings ordered by points
- `GET /api/players/point-streaks?season=` - Get players with active point streaks
- `GET /api/players/hot?season=` - Get "hot" players based on recent performance
- `GET /api/players/{playerId}?season=` - Get specific player details
- `GET /api/players/{playerId}/game-log?season=` - Get player game-by-game log

### Search Endpoints

- `GET /api/search/all?season=` - Get all players and teams for search/autocomplete

See [API_REFERENCE.md](API_REFERENCE.md) for full endpoint documentation with example responses.

## API Documentation

Once the API application is running, access the interactive API documentation at:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## Configuration

Configuration can be modified in each module's `src/main/resources/application.yml`:

- `server.port`: Change the server port (default: 8080 for api)
- `nhl.api.current-season`: Set the NHL season (format: YYYYYYYY)
- `nhl.api.connection-timeout`: API connection timeout in milliseconds
- `nhl.api.read-timeout`: API read timeout in milliseconds

## Data Flow

```
External NHL API
      |
data-job (fetch + sync)
      |
PostgreSQL 16 (persist data)
      |
api (query + serve)
      |
Clients (consume REST API)
```

## Hot Rating Calculation

The "hot rating" for players is calculated as the points-per-game average over their last 10 games. This provides a more recent view of player performance compared to season averages.

## Development

### Run tests

```bash
mvn test
```

### Build the project

```bash
mvn clean package
```

### Build without tests

```bash
mvn clean package -DskipTests
```

## Security Considerations

- All NHL API endpoints use HTTPS
- CORS is enabled for frontend integration
- No sensitive data is stored in the database

## License

This project is for educational purposes.
