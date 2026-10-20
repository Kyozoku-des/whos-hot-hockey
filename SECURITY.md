# Security

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub's
[private vulnerability reporting](https://github.com/Kyozoku-des/whos-hot-hockey/security/advisories/new) (Security tab →
Report a vulnerability). Do not open a public issue. Include the affected
component, steps to reproduce and the impact you expect. Reports are handled on a
best-effort basis; this is a personal project with no bug bounty.

The rest of this document describes the controls the project relies on. A passing
build or scanner does not prove absence of injection or availability under every
attack.

## Development and API errors

Vite listens on loopback by default. Its deny patterns cover sensitive files even
when a request carries a transformation query or Windows alternate path. Run
`npm run test:security` in `frontend`; CI exercises Linux and Windows. LAN exposure
requires an explicit `--host` override and should only be used on trusted networks.

Spring client errors retain their proper status and protocol headers without
echoing or error-logging exception details. Unexpected server failures still log
an exception and return a generic 500 response.

## Overload controls

nginx trusts client addresses only from the configured Caddy peer. IPv4 addresses
have individual budgets; IPv6 addresses share a budget per /64. The limits are:

| Traffic | Rate | Immediate burst |
|---|---:|---:|
| API, per client/network | 10 requests/s | 50 |
| API, all clients | 50 requests/s | 100 |
| Static pages/assets, per client/network | 30 requests/s | 100 |
| Static pages/assets, all clients | 200 requests/s | 400 |

There is also a server-wide cap of 128 active requests/connections as counted by
nginx's connection limiter. Excess traffic receives 429. The aggregate keys use
the configured server name, so arbitrary Host headers do not create new budgets.
Requests from a shared IPv6 network or IPv4 NAT share that network/address budget.

Per-address, IPv6-prefix and distributed-address regression tests run against the
actual nginx configuration with a local mock backend. Caddy tests also send
incomplete headers to the real HTTP redirect listener: it must close within the
configured 10-second header deadline and then still redirect normal requests.
The explicit HTTP site ensures that listener inherits the configured timeouts;
automatically generated listeners do not inherit Caddyfile server options.

Compose also caps CPU, memory (with no additional swap allowance) and process
counts. API/migration containers have 768 MiB, PostgreSQL and ingestion/backfill
1 GiB each, Caddy 256 MiB, frontend/provisioning 128 MiB, and Alloy 300 MiB.
These are ceilings rather than reservations. Raise limits only after checking host
headroom; an exhausted memory budget can terminate and restart a service.

The hosting provider's network DDoS protection complements these local limits,
timeouts and resource caps. The application's limits cannot protect the Internet
link itself.

## Logs and diagnostic agents

Caddy removes the request object and response headers from both access records
and the default logger (including proxy failures). Status, timing and size fields
remain available. nginx access logging is disabled and its server error log is
restricted to critical failures, because its fixed log format includes raw request
text. Raw URI/header logging is intentionally not available in normal production
diagnostics. The log tests send synthetic payloads through real Caddy/nginx
processes and check emitted records.

This reduces one input path; it does not make all logs, NHL data, feedback, source
files or issue text trustworthy. There is no application-owned LLM runtime here.
An agent's permissions must be enforced by the tool/session operator: use read-only
database/Grafana access, omit production secrets and deployment tools, and require
a separate authorized session for changes. A repository instruction or text filter
cannot enforce those external tool permissions. Do not give diagnostic agents the
host Docker socket; access to it is effectively administrative access even through
a read-only mount.

## Database identities

Production/Compose serving services do not run Flyway. A short-lived `migrate`
service owns that work, followed by idempotent role provisioning. `whoshot_api`
has SELECT access; `whoshot_ingest` can select/insert/update/delete application data
and use sequences, but cannot create/drop schema objects or alter migration
history. Neither role has administrative attributes or membership in other roles.
The read-only API connection default is additional protection; permission tests
also disable that default to verify the actual database grants.

The owner/password is retained for migrations and rollback. Production runtime
passwords must be distinct, non-default and at least 20 characters. See the
[deployment prerequisites](infra/server/README.md#before-deploying-the-issue-58-database-role-change).
Local direct JVM runs retain their development defaults; use the runtime role
credentials and disable Flyway when connecting them to a provisioned database.

The database runs from the official `postgres:17` image rebuilt without gosu
([`infra/database/Dockerfile`](infra/database/Dockerfile)). It starts directly as
the postgres user instead of starting as root and dropping privileges.

## Caddy rebuild

The release builds the standard Caddy modules from the committed Go module lock
files instead of using the upstream image's compiled binary, so embedded Go
dependencies can be updated. Alpine packages are upgraded during the build. CI
validates the Caddyfile and access-log filter using this exact image.

## Dependency gate

The security workflow derives the runtime inventory from **all** Compose profiles,
deduplicates shared images, and also scans the API, ingestion, frontend and Caddy
build stages. Trivy fails on MEDIUM/HIGH/CRITICAL findings unless an image-specific
policy in [`security/trivyignore`](security/trivyignore) accepts that exact package
version and location with a reason and expiry. Application runtime images have no
exceptions. The workflow retains suppressed findings in its reports, tests the
policy boundaries, and uploads the inventory and JSON reports. npm audit checks the
frontend lock file separately. Dependabot opens weekly update PRs for every
dependency source.
