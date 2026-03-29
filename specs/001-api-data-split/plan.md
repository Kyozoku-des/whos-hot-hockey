# Implementation Plan: Backend Modularization - API & Data Job Separation

**Branch**: `001-api-data-split` | **Date**: 2026-03-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-api-data-split/spec.md`

## Summary

Split the monolithic backend into two independent Spring Boot applications: an **API module** serving 5 REST endpoints to the Vue frontend, and a **data-job module** running as a standalone batch/scheduler process. Migrate from SQLite to PostgreSQL (Docker) to enable concurrent access from both applications. Implement last-10-games points percentage calculations with unit tests.

## Technical Context

**Language/Version**: Java 23, Spring Boot 3.5.6
**Primary Dependencies**: Spring Data JPA, Spring Web, Spring Retry, SpringDoc OpenAPI, Lombok, PostgreSQL Driver
**Storage**: PostgreSQL 16 (Docker) — migrating from SQLite
**Testing**: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL)
**Target Platform**: Local development (Docker Compose for PostgreSQL)
**Project Type**: Web service (multi-module Maven: domain, api, data-job)
**Performance Goals**: Homepage < 2s, search < 300ms, API responses < 500ms cached
**Constraints**: API and data-job must be independently deployable; shared domain module for entities
**Scale/Scope**: 32 teams, ~800 players, 1 concurrent user (personal project)

**Frontend**: Vue 3 (Composition API), Pinia, Vue Router, Chart.js, Vite — port 3000
**Backend API**: Spring Boot — port 8080
**Data-Job**: Spring Boot — long-running daemon with internal hourly scheduling (transitions to 1-min sync during games)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Test-First Development | PASS | Statistics calculations (FR-019, FR-020, FR-023) require unit tests. All API endpoints will have controller tests. Test strategy is integral to the plan. |
| II. Documentation-First | PASS | API contracts defined in `contracts/api-endpoints.md`. Plan and spec artifacts maintained. |
| III. Pragmatic Architecture | PASS | Two-module split is justified by independent deployment needs (API always-on vs data-job batch). PostgreSQL migration justified by concurrent access requirement. No additional abstraction layers introduced. |

### Post-Phase 1 Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Test-First Development | PASS | Unit tests for StatisticsCalculationService (player + team points percentage). Controller tests for all 5 endpoints. Repository integration tests with Testcontainers. |
| II. Documentation-First | PASS | Contracts, data model, and quickstart documented in specs directory. |
| III. Pragmatic Architecture | PASS | Existing service/repository pattern preserved. PostgreSQL migration is minimum change for concurrent access. Docker Compose keeps local setup simple. |

## Project Structure

### Documentation (this feature)

```text
specs/001-api-data-split/
├── plan.md              # This file
├── research.md          # Phase 0: technical decisions
├── data-model.md        # Phase 1: entity design
├── quickstart.md        # Phase 1: dev setup guide
├── contracts/
│   └── api-endpoints.md # Phase 1: REST API contracts
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                    # Parent POM (manages shared deps)
├── domain/                    # Shared entity + repository module
│   ├── pom.xml
│   └── src/main/java/com/whoshot/nhl/domain/
│       ├── entity/            # JPA entities (Player, Team, GameLog, TeamGame, CurrentSeason)
│       └── repository/        # Spring Data JPA repositories
├── api/                       # REST API application (port 8080)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/whoshot/nhl/api/
│       │   ├── ApiApplication.java        # NEW: own @SpringBootApplication
│       │   ├── config/
│       │   │   └── CorsConfig.java
│       │   ├── controller/
│       │   │   ├── SearchController.java  # existing
│       │   │   ├── PlayerController.java  # NEW
│       │   │   └── TeamController.java    # NEW
│       │   ├── dto/                       # Response DTOs
│       │   └── service/
│       │       ├── PlayerService.java     # NEW
│       │       └── TeamService.java       # NEW
│       └── test/java/com/whoshot/nhl/api/
│           ├── controller/                # Controller tests
│           └── service/                   # Service tests
└── data-job/                  # Data sync application (standalone)
    ├── pom.xml
    └── src/
        ├── main/java/com/whoshot/nhl/datajob/
        │   ├── DataIngestionJob.java      # existing main class
        │   ├── service/
        │   │   ├── StatisticsCalculationService.java  # enhanced with team calcs
        │   │   └── ...
        │   └── ...
        └── test/java/com/whoshot/nhl/datajob/
            └── service/
                └── StatisticsCalculationServiceTest.java  # NEW: unit tests

frontend/
└── src/
    ├── composables/useApi.js      # existing — endpoints already defined
    ├── components/                # existing — already built
    └── views/                     # existing — already built

docker-compose.yml                 # NEW: PostgreSQL container
```

**Structure Decision**: Existing multi-module Maven structure is preserved. The API module gets its own `@SpringBootApplication` and is decoupled from the data-job module dependency. Both modules depend on `domain` for shared entities/repositories. The API module does NOT depend on data-job (removing current circular dependency).

## Complexity Tracking

| Decision | Why Needed | Simpler Alternative Rejected Because |
|----------|------------|--------------------------------------|
| PostgreSQL migration | API and data-job need concurrent DB access | SQLite limits connection pool to 1; cannot run two apps against same SQLite file reliably |
| Docker Compose | PostgreSQL needs a server process | Manual PostgreSQL install is less reproducible; Docker is standard for local dev |
| Separate @SpringBootApplication for API | API must run independently of data-job | Current design couples API into data-job's process; violates independent deployment requirement |
