# Who's Hot - NHL Statistics Backend

A Spring Boot backend service that provides REST APIs for NHL statistics with a focus on identifying "hot" and "cold" players and teams based on recent performance.

## Features

- **Team Standings**: View current NHL team standings
- **Player Statistics**: Access comprehensive player stats including points, goals, assists
- **Point Streaks**: Track players with active point streaks
- **Hot Players**: Identify players performing exceptionally well in recent games
- **Win/Loss Streaks**: Monitor teams on winning or losing streaks
- **Individual Pages**: Detailed statistics for specific players and teams

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Database**: SQLite
- **Build Tool**: Maven
- **Java Version**: 21
- **Documentation**: SpringDoc OpenAPI (Swagger UI)

## Prerequisites

- Java 21 or higher
- Maven 3.9+

## Getting Started

### Build the project

```bash
mvn clean package
```

### Run the application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

### Initial Data Load

On startup, the application automatically fetches data from the NHL API and populates the database. This may take a few minutes.

## API Endpoints

### Team Endpoints

- `GET /api/teams/standings` - Get team standings ordered by points
- `GET /api/teams/win-streaks` - Get teams with active win streaks
- `GET /api/teams/loss-streaks` - Get teams with active loss streaks
- `GET /api/teams/{teamCode}` - Get specific team details
- `GET /api/teams/{teamCode}/players` - Get all players on a team

### Player Endpoints

- `GET /api/players/standings` - Get player standings ordered by points
- `GET /api/players/point-streaks` - Get players with active point streaks
- `GET /api/players/hot` - Get "hot" players based on recent performance
- `GET /api/players/{playerId}` - Get specific player details

### Data Sync Endpoints

- `POST /api/data/sync` - Manually trigger full data synchronization
- `POST /api/data/sync/standings` - Sync only team standings
- `POST /api/data/sync/players` - Sync only player statistics

## API Documentation

Once the application is running, access the interactive API documentation at:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## Configuration

Configuration can be modified in `src/main/resources/application.yml`:

- `server.port`: Change the server port (default: 8080)
- `nhl.api.current-season`: Set the NHL season (format: YYYYYYYY)
- `nhl.api.connection-timeout`: API connection timeout in milliseconds
- `nhl.api.read-timeout`: API read timeout in milliseconds

## Data Flow

```
External NHL API
      ↓
Integration Service (fetch data)
      ↓
SQLite Database (persist data)
      ↓
Repository Layer (query data)
      ↓
Service Layer (business logic + calculations)
      ↓
REST Controllers (expose endpoints)
      ↓
Clients (consume REST API)
```

## Hot Rating Calculation

The "hot rating" for players is calculated as the points-per-game average over their last 10 games. This provides a more recent view of player performance compared to season averages.

## Development

### Run tests

```bash
mvn test
```

### Build without tests

```bash
mvn clean package -DskipTests
```

## Security Considerations

- All NHL API endpoints use HTTPS
- CORS is enabled for frontend integration
- No sensitive data is stored in the database
- Database file should be excluded from version control

## License

This project is for educational purposes.
