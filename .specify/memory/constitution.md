<!--
Sync Impact Report
==================
Version Change: Initial Creation → 1.0.0
Ratification Date: 2026-03-08
Last Amended: 2026-03-08

New Constitution with 3 Core Principles:
1. Test-First Development (NON-NEGOTIABLE)
2. Documentation-First
3. Pragmatic Architecture

Templates Status:
✅ plan-template.md - Constitution Check section present, compatible
✅ spec-template.md - Requirements sections align with documentation principle
✅ tasks-template.md - Test-first workflow compatible, user story structure supports principles
⚠️ command files - Generic principles apply, no agent-specific references needed

Follow-up Actions:
- Validate that all future design documents reference this constitution
- Ensure plan.md Constitution Check gates are applied during /speckit.plan
- Monitor adherence during implementation phases
-->

# Who's Hot NHL Constitution

## Core Principles

### I. Test-First Development (NON-NEGOTIABLE)

Every feature implementation MUST follow the Test-Driven Development cycle:

- **Tests written FIRST**: Before any implementation code is written, tests must be created that define expected behavior
- **Tests must FAIL initially**: Verify that tests fail before implementation to prove they're testing something
- **Red-Green-Refactor cycle**: Write failing test (Red) → Implement minimum code to pass (Green) → Refactor for quality (Refactor)
- **No exceptions**: This principle is non-negotiable for all backend business logic, API endpoints, data models, and critical frontend components

**Rationale**: Test-first development catches bugs early, ensures code meets requirements, provides living documentation, and enables confident refactoring. For a sports statistics application with complex calculations (streaks, hot ratings, game logs), tests are essential to verify correctness.

**Applies to**:
- Backend API endpoints (Spring Boot controllers and services)
- Business logic calculations (hot rating, streak detection, points-per-game)
- Data models and repositories
- Frontend critical paths (API integration, data transformations, key user interactions)

### II. Documentation-First

All modules, features, and APIs MUST be fully documented before and during development:

- **API contracts**: All REST endpoints documented in `API_CONTRACT.md` with request/response schemas
- **README files**: Each major module (backend/, frontend/) maintains a README with setup, architecture, and development instructions
- **Architecture decisions**: Significant design choices documented in plan.md or ADR format
- **Inline documentation**: Public methods, complex algorithms, and non-obvious code include explanatory comments
- **User-facing docs**: Feature documentation and quickstart guides kept current

**Rationale**: Full documentation enables team collaboration, onboarding, maintenance, and provides a single source of truth. With frontend and backend teams working independently, API contracts are critical for coordination.

**Required artifacts**:
- `API_CONTRACT.md` - REST API specifications (maintained)
- `backend/README.md` - Backend architecture, setup, data flow
- `frontend/README.md` - Frontend structure, component design, routing
- `.specify/` design documents - Specifications, plans, tasks for each feature
- Inline comments for business logic (e.g., hot rating formula, streak calculations)

### III. Pragmatic Architecture

Design decisions MUST balance simplicity with legitimate complexity:

- **Complexity requires justification**: Any abstraction, pattern, or architectural complexity must provide clear, measurable value
- **Design patterns permitted when appropriate**: Repository pattern, service layer, composables, etc. are allowed if they solve real problems
- **YAGNI awareness**: Don't build for hypothetical future requirements, but do design for known extensibility needs
- **Refactoring is encouraged**: When complexity proves unnecessary, simplify aggressively
- **Document trade-offs**: When choosing complexity, document why simpler alternatives were insufficient

**Rationale**: This project requires moderate sophistication (REST APIs, database access, state management, charts) but shouldn't be over-engineered. Balance developer productivity with maintainability.

**Acceptable complexity**:
- Service layer separation (business logic from controllers)
- Repository pattern (data access abstraction)
- Vue composables (shared state and logic)
- DTO transformations (API contract enforcement)

**Requires justification**:
- Additional abstraction layers beyond service/repository
- Custom framework/library wrappers
- Complex caching strategies
- Event-driven architectures
- Microservices decomposition

## Technology Standards

The following technology stack is established for consistency:

**Backend**:
- Language: Java 21
- Framework: Spring Boot 3.2.0
- Database: SQLite
- Build Tool: Maven 3.9+
- Testing: JUnit 5, Spring Boot Test
- Documentation: SpringDoc OpenAPI (Swagger UI)

**Frontend**:
- Language: JavaScript (ES6+)
- Framework: Vue 3 with Composition API
- Build Tool: Vite
- Router: Vue Router
- Testing: Vitest (when tests are implemented)
- Charts: Chart.js with vue-chartjs

**Integration**:
- API Protocol: REST over HTTP
- Data Format: JSON
- CORS: Enabled for localhost development

**Rationale**: Stack has been established in initial implementation. Changes require migration plan and approval.

## Development Workflow

### Feature Development Process

1. **Specification** (`/speckit.specify`): Define user stories, requirements, success criteria
2. **Clarification** (`/speckit.clarify`): Resolve ambiguities before design
3. **Planning** (`/speckit.plan`): Research, design data models, create contracts
4. **Task Generation** (`/speckit.tasks`): Break down into actionable, testable tasks
5. **Implementation** (`/speckit.implement`): Execute tasks with test-first approach
6. **Validation**: Verify against specification and success criteria

### Constitution Compliance Gates

Each phase includes constitution checks:

- **Planning Phase**: Verify test strategy, documentation plan, architecture justification
- **Task Generation**: Ensure test tasks precede implementation tasks
- **Implementation**: Red-Green-Refactor cycle, documentation updates, complexity review
- **Review**: Validate all principles before feature completion

### Branching and Commits

- Feature branches: `###-feature-name` format
- Atomic commits: Each task or logical unit
- PR requirements: Tests passing, documentation updated, constitution compliance verified
- Main branch: `master` (protected)

## Governance

### Authority and Amendments

This constitution supersedes all other practices and preferences. All development work must comply with these principles.

**Amendment procedure**:
1. Propose change with rationale and impact analysis
2. Update constitution using `/speckit.constitution` command
3. Document version change and principle modifications
4. Propagate changes to dependent templates and guidance
5. Update all in-flight design documents if affected

**Version numbering** (semantic versioning):
- **MAJOR**: Backward incompatible changes (principle removal/redefinition)
- **MINOR**: New principle added or material guidance expansion
- **PATCH**: Clarifications, wording improvements, non-semantic changes

### Compliance and Enforcement

- All PRs/code reviews must verify compliance with all three core principles
- Constitution violations must be corrected before merge
- Complexity (Principle III) requires documented justification in plan.md Complexity Tracking table
- Test-first violations (Principle I) are grounds for immediate PR rejection
- Documentation gaps (Principle II) must be resolved before feature completion

### Conflict Resolution

When principles appear to conflict:
1. **Principle I (Test-First)** takes precedence - tests are non-negotiable
2. **Principle II (Documentation)** ensures decisions are recorded
3. **Principle III (Pragmatic Architecture)** provides flexibility for justified complexity

### Living Document

This constitution is a living document. Use the `/speckit.constitution` command to propose updates. All dependent templates in `.specify/templates/` will be checked and updated for consistency.

**Version**: 1.0.0 | **Ratified**: 2026-03-08 | **Last Amended**: 2026-03-08
