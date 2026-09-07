# Local PostgreSQL and Flyway

From the repository root, with Docker Desktop running:

```powershell
docker compose up -d --wait
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
`V1__create_nhl_schema.sql` creates the five entity tables. Hibernate uses
`ddl-auto=validate` and never creates or alters tables itself.

Add future changes as `V2__description.sql`, `V3__description.sql`, etc. Never edit
a migration after it has been applied; Flyway checks its checksum. The
`flyway_schema_history` table records applied versions. Flyway coordinates
concurrent startup against the same database.

This creates a fresh PostgreSQL database; it does not import SQLite records.
For later changes, prefer additive migrations compatible with the previous app
version. Roll back application code only while its schema remains compatible;
otherwise apply a corrective migration or restore a backup. Flyway clean is disabled.

## Verify migrations and entity mappings

With the container running, execute from the repository root:

```powershell
$env:RUN_POSTGRES_TESTS = 'true'
mvn -f backend/pom.xml -pl data-job -am test '-Dtest=PostgresMigrationTest,DataSyncServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false'
Remove-Item Env:RUN_POSTGRES_TESTS
```

The PostgreSQL test applies pending migrations to the configured database,
validates Hibernate mappings, checks a repeated migration run, and tests identity
and composite-key persistence. Test records are rolled back. NHL synchronization
is mocked, so this test makes no NHL requests and does not ingest player data.
