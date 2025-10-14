# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Purpose

This repository is only used for backend development.

## Application overview

The app that is to be developed is a web application that shows NHL statistics. With the USP of calculating which teams and players are "hot" or "cold" right now.

## Agent instructions

1. Only backend agent should use this CLAUDE.md file
2. Backend agent can only write code to this branch
3. Backend agent can only develop to /backend sub-folder
4. Backend agent can only push to backend branch
5. Backend agent is allowed to pull changes from master branch, but not push to any other branch than backend branch
6. Backend agent needs to pull changes from master before developing anything new to allign that the changes will work with the frontend

### Requirements of what app should have:

- A table that shows team standings of the current year.
- A table that shows player point standings of the current year. It should include points, games played, goals, and assists at least
- A table that shows current points streaks of players. This needs to be calculated.
- A table that determines how "hot" a player is right now based on point per games during a certain amount of games.
- A table that shows team win streaks.
- A table that shows team losing streaks.
- When clicking on a player, you should be directed to a new page with statistics about that player.
- When clicking on a team, you should be directed to a new page with statistics about that team.

## Backend specifications

- Use Spring Boot

**Key Points**:

- External APIs are documented in `API_REFERENCE.md`
- Data fetching should be scheduled or triggered appropriately
- SQLite database is the single source of truth for the REST API
- REST endpoints read from SQLite, not from external APIs directly
- Make sure to fetch the relevant data once to fill up the database
- Don't store unneccessary data for this use case

## Common Spring Boot Tech Stack

- **Framework**: Spring Boot 3.x / 2.x
- **Build Tools**: Maven
- **Database**: SQLite (primary database for this project)
- **HTTP Client**: RestTemplate, WebClient (for external API calls)
- **Security**: Spring Security, JWT, OAuth2
- **Testing**: JUnit 5, Mockito, TestContainers
- **Documentation**: SpringDoc OpenAPI
- **Messaging**: Kafka, RabbitMQ
- **Caching**: Redis, Caffeine
- **Monitoring**: Actuator, Micrometer, Prometheus

## Data Flow

```
External APIs (documented in API_REFERENCE.md)
          ↓
   Integration Service (fetch data)
          ↓
   SQLite Database (persist data)
          ↓
   Repository Layer (query data)
          ↓
   Service Layer (business logic)
          ↓
   REST Controllers (expose endpoints)
          ↓
   Clients (consume REST API)
```

## AI Agent Workflow

### Before Making Changes

**STEP 0 - PULL FROM MASTER (MANDATORY):**

**After pulling from master:**

1. **Check for breaking changes**: Review frontend commits to allign code

**Then proceed with:**

1. **Understand the context**: Read related files and existing patterns
2. **Follow conventions**: Match the existing code style and structure

### When Writing Code

1. **Stay consistent**: Match indentation, quote style, and formatting
2. **Use existing utilities**: Don't reinvent existing methods or classes
3. **Preserve functionality**: Ensure changes don't break existing features
4. **Add comments**: Explain complex logic or business rules
5. **Handle errors**: Add proper error handling

### After Making Changes

1. **Test locally**: Verify the application runs without errors
2. **Verify endpoints**: Ensure endpoints work and give the expected responses
3. **Review changes**: Confirm all modifications align with requirements

## Resources

- [Spring Boot Official Documentation](https://spring.io/projects/spring-boot#overview)

### Additional, but important

- Have security in mind when you work. For example don't use any unsecure references, URL:s or dependencies
