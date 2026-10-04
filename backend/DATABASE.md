# Local PostgreSQL and Flyway

From the repository root, with Docker Desktop running:

```powershell
docker compose up -d --wait postgres
docker compose ps
```

Start `DataIngestionJob` from VS Code as usual. Both backend modules use:

| Setting | Local default |
| --- | --- |
| Host | localhost |
| Port | 5432 |
| Database | nhl_stats |
| Username | whoshot |
| Password | whoshot_local |

These credentials are for local development. The port is bound to loopback only.
Override application settings with `DB_URL`, `DB_USER`, and `DB_PASSWORD`.
Compose also reads `DB_PASSWORD` when first initializing its volume; changing it
later does not change an existing PostgreSQL role's password.

Data persists in the `whos-hot-nhl_postgres_data` Docker volume.
`docker compose stop` stops the database. `docker compose down` removes the
container but retains its data. Do not add `--volumes` unless you intend to erase it.

## Schema changes

Flyway runs automatically before Hibernate on backend startup. Shared migrations
live in `domain/src/main/resources/db/migration`, available to both modules.
`V1__create_nhl_schema.sql` creates the five entity tables;
`V2__drop_unpopulated_columns.sql` drops columns that no ingestion path ever wrote.
`V3__consistent_season_and_timestamp_columns.sql` names the season column `season_id` in every
table and stores every `last_updated` as a timestamp.
Hibernate uses `ddl-auto=validate` and never creates or alters tables itself.

Add future changes as `V4__description.sql`, `V5__description.sql`, etc. Never edit
a migration after it has been applied; Flyway checks its checksum. The
`flyway_schema_history` table records applied versions. Flyway coordinates
concurrent startup against the same database.

This creates a fresh PostgreSQL database; it does not import SQLite records.
For later changes, prefer additive migrations compatible with the previous app
version. Roll back application code only while its schema remains compatible;
otherwise apply a corrective migration or restore a backup. Flyway clean is disabled.

## Verify migrations and entity mappings

`PostgresMigrationTest` runs with the regular test suite against a disposable
Testcontainers PostgreSQL (Docker required). It applies every migration to an
empty database, validates the Hibernate mappings, checks that a repeated migration
run is a no-op, and tests identity and composite-key persistence. It never touches
your local database and makes no NHL requests.

```powershell
mvn -f backend/pom.xml -pl data-job -am test '-Dtest=PostgresMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```
