# Who's Hot NHL Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-03-27

## Active Technologies

- **Language**: Java 23
- **Backend Framework**: Spring Boot 3.5.6
- **Dependencies**: Spring Data JPA, Spring Web, Spring Retry, SpringDoc OpenAPI, Lombok, PostgreSQL Driver
- **Database**: PostgreSQL 16 (Docker) — migrating from SQLite
- **Testing**: JUnit 5, Spring Boot Test, Testcontainers
- **Frontend**: Vue 3 (Composition API), Pinia, Vue Router, Chart.js, Vite
- **Build**: Maven 3.9+ (multi-module), npm

## Project Structure

```text
backend/
├── pom.xml              # Parent POM
├── domain/              # Shared entities & repositories
├── api/                 # REST API module (port 8080)
└── data-job/            # Data sync module (standalone process)

frontend/
└── src/
    ├── composables/     # API hooks, favorites
    ├── components/      # Reusable UI components
    ├── views/           # Page components
    ├── stores/          # Pinia stores
    └── router/          # Vue Router config

specs/                   # Feature specifications
.specify/                # Spec-kit templates and scripts
```

## Commands

```bash
# Backend
cd backend && mvn clean install          # Build all modules
cd backend/api && mvn spring-boot:run    # Run API (port 8080)
cd backend/data-job && mvn spring-boot:run  # Run data sync
cd backend && mvn test                   # Run all tests

# Frontend
cd frontend && npm run dev               # Dev server (port 3000)
cd frontend && npm run build             # Production build

# Database
docker compose up -d                     # Start PostgreSQL (port 5432)
docker compose down                      # Stop PostgreSQL
```

## Code Style

### Java
- Lombok for boilerplate (getters, setters, builders, constructors)
- Spring Data JPA repositories with custom `@Query` methods
- Service layer pattern (services orchestrate, repositories access data)
- DTOs for API responses, entities for persistence
- `@Retryable` with exponential backoff for external API calls

### Vue/JavaScript
- Composition API with `<script setup>` where applicable
- Composables for shared logic (`useApi`, `useFavorites`)
- Pinia for global state management
- CSS variables for theming (dark retro theme)

## Constitution

See `.specify/memory/constitution.md` for core principles:
1. **Test-First Development** (NON-NEGOTIABLE): Red-Green-Refactor cycle
2. **Documentation-First**: API contracts, READMEs, inline docs
3. **Pragmatic Architecture**: Justify complexity, YAGNI

## Recent Changes

- **001-api-data-split**: Backend modularization — splitting API and data-job into independent applications, PostgreSQL migration

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
