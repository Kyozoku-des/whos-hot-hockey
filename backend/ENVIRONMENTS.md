# Local and production environments

Both applications have `application-local.properties` and `application-prod.properties`.
`local` is the default when no profile is selected. Select exactly one environment;
combine it with a job mode when needed: `local,initial-load`, `local,backfill`,
`prod,initial-load`, or `prod,backfill`. A job mode by itself does not activate `local`.

## Local Compose

From the repository root, copy the template once and start the services:

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

Do not overwrite an existing `.env`. Compose reads the root `.env`; it does not use
`backend/.env`. `DB_PASSWORD` is passed to PostgreSQL and both backend containers.
Changing it does not change the password in an already initialized database volume.

API and job logs persist in `backend/logs/` through container recreation. Read them with:

```powershell
Get-Content backend/logs/api.log -Tail 100 -Wait
# In another terminal:
Get-Content backend/logs/data-job.log -Tail 100 -Wait
```

For a second worktree, set unused `DB_PORT`, `API_PORT`, and `FRONTEND_PORT` values
in that worktree's root `.env` and use a separate project name on **every** command:

```powershell
docker compose -p nhl-environments up -d --build
docker compose -p nhl-environments down
```

This separates containers, networks, and database volumes. Container-to-container
ports stay unchanged. All published ports bind to localhost. `down` keeps the database
volume and the host log directory; do not add `--volumes` to retain the database.
Podman users can replace `docker compose` with `podman-compose`.

## Local Java processes

Start PostgreSQL from the repository root, then build the executable JARs:

```powershell
docker compose up -d postgres
mvn -f backend/pom.xml package '-DskipTests'
Set-Location backend
Copy-Item .env.example .env
java -jar api/target/api-1.0.0-SNAPSHOT.jar
```

In a second terminal, also from `backend/`:

```powershell
java -jar data-job/target/data-job-1.0.0-SNAPSHOT.jar
```

The local profile imports `backend/.env` when launched from `backend/`. It is optional:
the existing local database defaults work without it. Edit `DB_URL` if PostgreSQL
uses a different host port and keep `DB_PASSWORD` consistent with Compose.
The file uses **Java properties syntax**, not shell syntax: unquoted `KEY=value`,
no `export`, forward slashes in paths, and no inline comments. Process environment
variables override imported values. Git ignores real `.env` files and log directories;
the backend Docker build excludes them as well.

For IDE or Maven runs with a different working directory, set `BACKEND_ENV_FILE`
to the absolute path of `backend/.env` and use an absolute `LOG_DIRECTORY` in that file
(for example `C:/dev/nhl/logs`). `BACKEND_ENV_FILE` must be supplied before startup,
not inside the file it locates. The template's `SERVER_PORT` applies to the API only.

The API writes `logs/api.log`; the daemon writes `logs/data-job.log`, plus console
output. Each rotates daily or at 10 MB, retains at most 14 days of compressed archives,
and caps archives at 200 MB per application. The current log is additional to that cap.
Logs are relative to the process working directory unless `LOG_DIRECTORY` is absolute.
SQL statement logging is off by default to avoid flooding long-running logs.

Stop the daemon before running a one-off load so the jobs do not write concurrently:

```powershell
# From backend/, with the native daemon stopped:
java -jar data-job/target/data-job-1.0.0-SNAPSHOT.jar --spring.profiles.active=local,initial-load
java -jar data-job/target/data-job-1.0.0-SNAPSHOT.jar --spring.profiles.active=local,backfill --backfill.season=20242025
```

For Compose, run from the repository root:

```powershell
docker compose stop data-job
docker compose run --rm -e SPRING_PROFILES_ACTIVE=local,initial-load data-job
# Or run a historical backfill (its log is backend/logs/backfill.log):
docker compose run --rm -e BACKFILL_SEASON=20242025 backfill
docker compose up -d data-job
```

## Frontend

No environment-specific API URL is needed for the current same-origin setup.
Vite proxies `/api` to localhost:8080; nginx proxies it to `backend-api:8080` in Compose.
For native development, run `npm ci` then `npm run dev` from `frontend/`.
If the API uses another local port, change the Vite proxy target for that run.
`frontend/.env.example` documents `VITE_API_BASE_URL` for deliberately using a separate
origin. Vite embeds it at **build time**, so never put secrets in `VITE_*` values.
Separate-origin deployments also need a deliberate CORS policy; same-origin hosting
avoids that requirement.

## Production preparation

The root Compose file is for local development. Hosting, CI/CD, TLS termination,
secret provisioning, database backups, and the log collector remain undecided.
No cloud resources or production credentials are required for this preparation.

Build the existing `api` and `data-job` targets from `backend/Dockerfile`. Configure
each deployed process with the values shown in `backend/.env.prod.example`:

| Variable | Required value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` (or `prod,initial-load` / `prod,backfill` for one-off jobs) |
| `DB_URL` | Production PostgreSQL JDBC URL, including host-required TLS options |
| `DB_USER` | Provisioned database role |
| `DB_PASSWORD` | Secret supplied by the hosting platform |

The template deliberately leaves unknown values empty. Do not deploy it unchanged.
Production does not import the local `.env` or fall back to local database credentials.
Standard `SPRING_DATASOURCE_*` process variables are also supported by Spring Boot.
Inject values through the platform's environment/secret mechanism; for local container
verification, `docker run --env-file backend/.env.prod ...` reads a privately populated
copy of the template. Merely creating `.env.prod` does not make native Java load it.

Both production applications log to stdout using the shared `logback-console.xml`.
There is no file appender, even if `LOGGING_FILE_NAME` is accidentally inherited.
The hosting platform can forward stdout to Azure Log Analytics or another collector
when chosen. No cloud SDK or destination is hardcoded. Do not activate `local` with
`prod`, and do not override `LOGGING_CONFIG` in production.

Flyway remains enabled and Hibernate validates the schema. Plan the database role's
migration permissions before deployment. Verify the API's `/actuator/health` on the
private service endpoint; keep management endpoints and database ports private.
Keep `/api` on the frontend origin using the chosen platform's reverse proxy.

## Verification

The configuration contract tests load both applications' real profile files without
connecting to PostgreSQL or the NHL API:

```powershell
mvn -f backend/pom.xml -pl api -am test '-Dtest=EnvironmentProfilesTest' '-Dsurefire.failIfNoSpecifiedTests=false'
docker compose config --quiet
```

After startup, confirm API health, check both local log files, and confirm production
logs appear in the process/container output without creating application log files.
