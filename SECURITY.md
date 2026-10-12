# Security controls and remaining work

Tracking issue: #58. A passing build or scanner does not prove absence of injection
or availability under every attack. Keep the issue open until the remaining
operational work and dependency findings are resolved.

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

These are conservative starting limits, not results of a production capacity test.
Monitor rejections, CPU, database connections and latency before increasing them.
Per-address, IPv6-prefix and distributed-address regression tests run against the
actual nginx configuration with a local mock backend. Caddy tests also send
incomplete headers to the real HTTP redirect listener: it must close within the
configured 10-second header deadline and then still redirect normal requests.
The explicit HTTP site ensures that listener inherits the configured timeouts;
automatically generated listeners do not inherit Caddyfile server options.

Compose also caps CPU, memory (with no additional swap allowance) and process
counts. API/migration containers have 768 MiB, PostgreSQL and ingestion/backfill
1 GiB each, Caddy 256 MiB, frontend/provisioning 128 MiB, and Alloy 300 MiB.
These are ceilings rather than reservations. The API and migration startup were
smoke-tested under these limits; sustained ingestion/backfill and production load
still need measurement. On the 4 GB host, schedule backfills with attention to
concurrent service memory usage. Raise limits only after checking host headroom;
an exhausted memory budget can terminate and restart a service.

No paid external security service is required or planned. The existing deployment
uses Hetzner, whose [automated network DDoS protection](https://www.hetzner.com/unternehmen/ddos-schutz/)
is provided without an additional charge (provider documentation checked 2026-10-12).
Use that included protection alongside the local limits, timeouts and resource caps;
no new provider, CDN or paid dependency is added. This is not a guarantee that all
attacks will be stopped, and the application's limits cannot protect the Internet link.

**Still required:** measure production capacity and validate the local controls on
the deployed host. The origin still accepts direct Internet traffic. Any additional
protection must be included in the existing hosting or free of charge.

## Logs and diagnostic agents

Caddy removes the request object and response headers from both access records
and the default logger (including proxy failures). Status, timing and size fields
remain available. nginx access logging is
disabled and its server error log is restricted to critical failures, because its
fixed log format includes raw request text. Use Caddy status metrics and service
health for ordinary upstream failures; raw URI/header logging is intentionally not
available in normal production diagnostics. The log tests send synthetic payloads
through real Caddy/nginx processes and check emitted records.

This reduces one input path; it does not make all logs, NHL data, feedback, source
files or issue text trustworthy. There is no application-owned LLM runtime here.
An agent's permissions must be enforced by the tool/session operator: use read-only
database/Grafana access, omit production secrets and deployment tools, and require
a separate authorized session for changes. A repository instruction or text filter
cannot enforce those external tool permissions. Do not treat retrieved text as
authorization to run commands, alter data, disclose credentials or contact anyone.

**Still required:** verify those external diagnostic-tool permissions and test the
actual agent integration with adversarial fixtures before claiming an enforced
prompt-injection boundary. Do not give diagnostic agents the host Docker socket;
access to it is effectively administrative access even through a read-only mount.

## Database identities

Production/Compose serving services no longer run Flyway. A short-lived `migrate`
service owns that work, followed by idempotent role provisioning. `whoshot_api`
has SELECT access; `whoshot_ingest` can select/insert/update/delete application data
and use sequences, but cannot create/drop schema objects or alter migration
history. Neither role has administrative attributes or membership in other roles.
The read-only API connection default is additional protection; permission tests
also disable that default to verify the actual database grants.

The original owner/password is retained for migrations and rollback. Production
runtime passwords must be distinct, non-default and at least 20 characters.
See [deployment prerequisites](infra/server/README.md#before-deploying-the-issue-58-database-role-change).
Local direct JVM runs retain their development defaults; use the runtime role
credentials and disable Flyway when connecting them to a provisioned database.

## Caddy security rebuild

The release builds the standard Caddy 2.11.7 modules from the committed Go module
lock files with Go 1.27.2 and `golang.org/x/net` 0.60.0. Alpine packages are upgraded
during the build. This replaces the upstream image's vulnerable compiled binary;
updating OS packages alone cannot repair embedded Go dependencies. CI validates
the Caddyfile and access-log filter using this exact image. Publish/retain the
`whos-hot-nhl-caddy` package alongside the other release images for rollback.

## Dependency gate and unresolved findings

The security workflow derives the runtime inventory from **all** Compose profiles,
deduplicates shared images, and also scans the API, ingestion, frontend and Caddy
build stages. Trivy fails on MEDIUM/HIGH/CRITICAL findings unless the inherited
image-specific policy accepts that exact package version and location with a reason
and expiry. Application runtime images have no exceptions. The workflow retains
suppressed findings in its reports and tests the policy boundaries. It finishes the
remaining scans after a failure and uploads the inventory and JSON reports. npm audit checks
the frontend lock file separately. Build stages include embedded tool binaries and
plugin dependencies that a lock-file audit alone misses.

The application runtime images (API, ingestion, frontend and rebuilt Caddy) had no
medium-or-higher findings in the 2026-10-11 local scan. This is a dated scanner
result, not a guarantee about future advisories or exploitability. PostgreSQL 17,
Alloy 1.20.1-distroless and some build tools still have findings, including ones without a
published fix. Master now carries expiring policies accepting specific upstream
findings (PR #84). This follow-up removes the obsolete esbuild rule and its 51
exceptions, retaining only the 11 npm entries with corrected reasons and unchanged
package versions, paths and expiry. It accepts no new findings. Any finding outside
those exact policy entries still blocks deployment. Keep issue #58 open for
the outstanding remediation and operational work; do not hide images or findings.

Resolve PostgreSQL/Alloy updates and their embedded dependencies separately from
the database role rollout. Review compatibility, migration/rollback and monitoring
before replacing either image. Upstream patches, replacement tools or documented
reachability evidence are required for the remaining build-tool findings; deleting
scanner results or unused binaries after executing them is not remediation.

### Follow-up build-tool remediation (2026-10-11)

Maven compiler 3.16.0 and targeted clean/resources/Boot plugin dependency overrides
reduce each Java build stage from 15 findings to 3. Application dependency
management does not patch a plugin's private dependency graph. The remaining two
HIGH and one MEDIUM findings are Apache HTTP libraries shaded inside
`docker-java-transport-zerodep` 3.7.1, the latest published release checked here;
ordinary dependency overrides cannot replace classes inside that jar.

Vite 8.3.4 with Vue plugin 6.0.10 replaces the old esbuild toolchain. Explicit build
targets retain the previous Chrome 87/Edge 88/Firefox 78/Safari 14 browser floor.
CI and the container run `npm run check:targets`, parsing every built JavaScript
module as ES2020 with Acorn. Regression tests reject newer syntax in nested chunks
and empty builds. This checks syntax, not browser APIs or CSS; real browser
compatibility still needs testing. The Node 24.21 container and CI install the npm
version from `package.json` (`packageManager`), currently 12.2.0. Its build-stage findings drop from 71 to
11 (four HIGH, seven MEDIUM), all in npm's bundled dependencies; the critical
esbuild finding is removed. The app lock-file audit remains clean. These build-tool
fixes add no policy exceptions or runtime dependency changes.

Validation: 252 backend tests; Windows short-path and Linux file-boundary tests;
frontend build; desktop/mobile-width headless browser smoke with mocked API data.
This is not a real-device or production-load test. To roll back these build-tool
changes, restore their POM/package/config/Dockerfile changes together and rebuild;
they do not alter database schemas or stored data. PostgreSQL, Alloy, npm's bundled
libraries and the shaded Docker test client still have raw findings. The gate applies
master's existing expiring policies; this branch does not extend their acceptance.
