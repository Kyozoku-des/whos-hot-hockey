# Ingestion throughput and request policy

How `data-job` fetches from the NHL API in parallel without overloading it (issue #29). This
covers the initial load, the live daemon (hourly full sync and game-time polls) and backfill.

## How a run works

1. **Season-wide inputs first.** Standings and the skater-leaders list are fetched once per run
   and reused for the whole run. A full sync also fetches every team's roster in parallel and
   writes each one in its own transaction; rostered players missing from the leaders (no points
   yet, goalies) are added to the run without a points total to cross-check.
2. **Team schedules, in parallel.** Up to `ingestion.fetch.concurrency` workers fetch club
   schedules. The calling thread writes each team's completed games in its own short transaction.
   Players are not started until every team has been written or skipped. That barrier is what
   lets player game logs resolve `gameWon`. A team whose schedule fails stays missing: its players'
   `gameWon` stays unknown and is never guessed.
3. **Players, in parallel.** Workers fetch a player's profile, then (if the player is wanted) their
   game log. They also validate the log and recalculate totals. Workers only make HTTP calls and
   build plain values; they never touch the database.
4. **One writer.** The calling thread takes results in standings order, whatever order they finish
   in. It writes each player together with all of their game logs in one transaction, so a player
   is never committed without their logs. At most `2 × concurrency` results wait to be written, so
   a slow database holds back new fetches instead of buffering the season in memory.

### Partial runs

Live sync commits per record: while a sync runs, readers can see some players refreshed and others
not yet. A record that fails keeps its previous valid row:

| Failure | Effect |
|---|---|
| A player's request fails after retries, malformed log, ID mismatch | player skipped, old row kept |
| Standings and game-log totals disagree (upstream updated mid-game) | player **deferred** to the next sync, old row kept; validation is never relaxed |
| A team schedule fails | team skipped; its players' `gameWon` stays unknown |
| Database lost, or stop requested | run ends; queued work is cancelled; nothing half-written |

Each run logs one `[ingestion]` summary line (see [Observing a run](#observing-a-run)). The
`ingestion.last.success` gauge only advances for runs with no skipped records. Publishing a whole
season atomically would need staging tables or versioning, and is out of scope.

### Writer exclusion

Every writer of a season holds that season's PostgreSQL advisory lock while it writes:

- backfill;
- initial load;
- the daemon's full syncs;
- the daemon's game-time polls.

A backfill of the current season fails fast while the daemon is writing it. A daemon sync or poll
is skipped (and retried) while a backfill holds it. The lock lives in PostgreSQL, so it also works
across processes. Backfills of other seasons are not blocked. The lock holds one database
connection, so keep the pool larger than 2 (the default Hikari pool is 10).

## Request policy

Every outbound attempt, retries included, goes through one process-wide `RequestThrottle`:

- **In-flight cap.** At most `max-in-flight` requests run at once, across every mode and host. The
  HTTP connection pool is sized to the same number.
- **Start rate.** Request starts are paced to `requests-per-second`, with bursts of up to `burst`.
  The budget is shared by both NHL hosts, so using two hosts does not double it. The in-flight cap
  and the start rate are separate limits.
- **Retries.** Only `ApiClient` retries (Apache HttpClient's own retries are off). It retries GETs
  on transport failures and on `408`, `429`, `500`, `502`, `503` and `504`. Other `4xx` responses,
  unreadable payloads and cancellation fail at once.
- **Backoff.** Retries back off exponentially with jitter, and never start before a
  `Retry-After` (seconds or HTTP-date).
- **Cooldown.** A `429` or `503` cools the host down for every worker, not only the one that was
  throttled.
- **Deadline.** A request gives up after `max-attempts`, or when its next attempt could not start
  before `operation-deadline`. A `Retry-After` longer than the deadline fails at once, visibly,
  instead of retrying early.
- **Circuit.** `circuit-failure-threshold` consecutive transient failures open a host's circuit
  for `circuit-open-duration`. After that, one probe request is let through, and the circuit closes
  only if the probe succeeds.

### Stopping

Closing the application:

- interrupts the scheduler and the fetch workers;
- cancels queued fetches;
- aborts waits for the request budget, backoff and queue space;
- stops new writes and releases the season lock (it is released in a `finally` block).

A worker blocked reading a socket cannot be interrupted. It finishes within the HTTP timeouts
(connect 5 s, read 10 s); the fetch pool waits 5 s for its workers, the scheduler up to 15 s.

## Settings

| Property | Default | Meaning |
|---|---|---|
| `ingestion.fetch.concurrency` | `4` | fetch workers; `1` = sequential on the calling thread |
| `nhle.api.requests.max-in-flight` | `4` | concurrent requests per process; also the connection pool size |
| `nhle.api.requests.requests-per-second` | `10` | request starts per second per process, retries included |
| `nhle.api.requests.burst` | `5` | starts allowed back to back before pacing applies |
| `nhle.api.requests.max-attempts` | `3` | attempts per request, the first included |
| `nhle.api.requests.initial-backoff` | `1s` | first retry delay before jitter; doubles per retry |
| `nhle.api.requests.max-backoff` | `30s` | cap on one computed backoff |
| `nhle.api.requests.operation-deadline` | `60s` | total time per request, waits and retries included |
| `nhle.api.requests.circuit-failure-threshold` | `5` | consecutive transient failures that open a host's circuit |
| `nhle.api.requests.circuit-open-duration` | `30s` | how long an open circuit blocks a host before one probe |
| `ingestion.player-info-cache.ttl` | `30m` | how long game-time polls reuse a player profile |
| `ingestion.player-info-cache.max-entries` | `2000` | profile cache bound |

Set any of them as an environment variable in Spring's relaxed form, for example
`INGESTION_FETCH_CONCURRENCY=1` or `NHLE_API_REQUESTS_REQUESTS_PER_SECOND=5`.

`backfill.request-delay-ms` is deprecated and ignored: it paced records, not requests, and did not
cover retries. Setting it logs a warning. Use `nhle.api.requests.requests-per-second` instead.

The defaults are conservative starting points. They are not a measured optimum or an NHL-approved
rate: no NHL rate limit is documented or was verified.

### Several processes

The budget is per JVM. If the daemon and a backfill of another season run at the same time, their
budgets add up. Give each process a share whose sum stays within what you are willing to send, for
example 6 req/s for the daemon and 4 req/s for the backfill. Running several replicas against the
same upstream would need a shared limiter; an in-memory one is not enough.

## Reducing work

- **Team scope.** Game-time polls fetch standings once for all selected teams and fetch
  season schedules only for finished teams (including failed writes awaiting retry).
  Empty team selections make no requests. Hourly and final full syncs intentionally
  re-read every schedule to repair missed games and upstream result corrections.
- **Profile reuse.** Game-time polls reuse a player's profile for up to the cache TTL. Without it,
  every poll re-requests the profile just to confirm the player is still active and on a playing
  team. Game logs and standings are always fetched fresh. Every full sync (at least hourly)
  refreshes every profile. Backfill never uses the cache, because historical participation must
  not depend on a player's current team or status.
- **One query per player or team.** Writing game logs or team games loads that player's or team's
  existing rows for the season in one query, instead of one lookup per game.
  `GameLogWriteStatementCountIT` measures it: rewriting a player with 40 game logs takes
  2 prepared statements, where the per-game lookup needed at least 41.
- **Inserts are not batched.** `GameLog` uses `IDENTITY` ids, so Hibernate cannot batch inserts;
  only updates can use `hibernate.jdbc.batch_size`.
- **Quieter logs.** Per-request `Fetching ...` log lines are at `DEBUG`. Use the metrics and the
  run summary line instead.

## Observing a run

Each run logs one summary line (illustrative values):

```
[ingestion] players-full run in 41250 ms: 1398 requests (33.9 /s), 2 retries, throttle wait 812 ms, db 3120 ms; 699 written, 41 filtered, 1 skipped (1 deferred); previous complete run 3611s ago
```

The modes are `players-full`, `players-scoped`, `team-schedules` and `backfill`.

Micrometer metrics (labels are fixed categories, never player or team ids):

| Metric | Type | Tags |
|---|---|---|
| `nhl.api.requests` | timer, p50/p95 | `endpoint`, `outcome`, `status` |
| `nhl.api.retries` | counter | `endpoint`, `status` |
| `nhl.api.throttle.wait` | timer | — |
| `nhl.api.in.flight` | gauge | — |
| `ingestion.fetch.outstanding` | gauge | — |
| `ingestion.run` | timer | `mode` |
| `ingestion.persist` | timer (database time per record) | `mode` |
| `ingestion.records` | counter | `mode`, `result` (`written`, `filtered`, `skipped`, `deferred`) |
| `ingestion.last.success` | gauge, epoch seconds | `mode` |

## Tuning evidence

`IngestionBenchmarkTest` runs a full player sync of 32 players through the real request factory,
`ApiClient`, throttle and pipeline:

- the HTTP calls go to a local stub that answers after 30 ms;
- each player write takes 2 ms;
- the rate budget is high and the in-flight cap is 8 in every run, so only the worker count varies.

Measured on a development laptop (median and worst of 3 runs):

| Workers | Median | Worst | Requests/run | Peak in flight | HTTP p50 | HTTP p95 |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2254 ms | 2496 ms | 64 | 1 | 32 ms | 34 ms |
| 2 | 1046 ms | 1075 ms | 64 | 2 | 31 ms | 34 ms |
| 4 | 534 ms | 535 ms | 64 | 4 | 31 ms | 32 ms |
| 8 | 283 ms | 288 ms | 64 | 8 | 31 ms | 34 ms |

Four workers are about 4.2 times faster than one here. The test fails below 2 times.

This scenario is deliberately bound by latency. Against the public API the request rate limits
throughput first: at the default 10 req/s, a full sync of about 700 players (2 requests each) needs
about 140 s however many workers run. The gain over sequential fetching therefore depends on the
real latency and on the rate you choose. Measure it in staging before raising either.

## Rollout and rollback

1. Deploy with `ingestion.fetch.concurrency=1`. Confirm that the `[ingestion]` lines and metrics
   look normal and that no rows go missing.
2. Raise it to `2`, then `4`, during a low-traffic period. Watch error and retry rates,
   `nhl.api.throttle.wait`, `429`/`503` counts, run duration and freshness (`ingestion.last.success`).
3. Raise `requests-per-second` only if throttle wait dominates the run time and the upstream shows
   no throttling. Choose the smallest setting near the throughput plateau.

To roll back, set `INGESTION_FETCH_CONCURRENCY=1`. Every mode then fetches sequentially on the
calling thread, as before. The request budget and retry policy stay active either way.
