# Monitoring

Production monitoring for issue #70: traffic, CPU and memory, failures, and alerts when the
site is down. It runs on **Grafana Cloud** (free tier). One small agent on the server,
[Grafana Alloy](https://grafana.com/docs/alloy/latest/), ships everything there; dashboards,
alert rules and uptime checks live in Grafana Cloud.

```
                 ┌──────────── VPS (docker compose --profile prod) ────────────┐
  visitors ──▶   │ caddy ─▶ frontend (nginx) ─▶ backend-api ──┐                │
                 │   │ :2019 metrics      :8081 actuator ─────┤                │
                 │   │                    data-job ── OTLP ───┤                │
                 │   └── logs (stdout) ───────────────────────┼─▶ alloy ───────┼──▶ Grafana Cloud
                 │            host /proc, /sys, docker.sock ──┘                │    (metrics, logs,
                 └─────────────────────────────────────────────────────────────┘     alerts, uptime)
```

## What is collected

| Need (#70) | Source | Where to look |
|---|---|---|
| Traffic | Caddy request metrics (`caddy_http_requests_total`, status codes, latency) and Caddy access logs (paths, referrers) | Metrics and Loki |
| CPU, memory, disk | Host: `node_*` (CPU, memory, swap, load, filesystem, disk IO, pressure). Containers: `container_*` per Compose `service` (CPU, memory, restarts, OOM) | Metrics |
| App health | API: JVM memory, GC, HTTP requests by endpoint and status, DB pool (`/actuator/prometheus` on the unpublished management port 8081). Data job: ingestion metrics pushed over OTLP (`ingestion_*`, `nhl_api_*`, freshness via `ingestion_last_success`) | Metrics |
| Failures | Every container's logs with `service` and `level` labels; Java stack traces kept as one entry. HTTP 5xx rates, NHL API errors, skipped/deferred ingestion records | Loki and metrics |
| Down / crucial failures | Grafana Cloud uptime checks from outside the server, plus alert rules on the data above | Alerting |

Uptime checks have to run outside the server: a server that is down can't report it.

Everything is labelled `environment="prod"`. Host and container metrics are filtered in
`alloy/config.alloy` to what is used, to stay well inside the free tier (10k metric series,
50 GB logs, 14-day retention).

## Setup

### 1. Grafana Cloud stack

1. Create a free account at [grafana.com](https://grafana.com) and a stack in an **EU** region.
2. Under **My Account → stack → Prometheus → Details**, note the remote write URL and the
   username (a number). Do the same under **Loki**.
3. Under **Access Policies**, create a policy with `metrics:write` and `logs:write` for the
   stack, and a token for it.

### 2. Server `.env`

Add to the server's `.env` next to `compose.yaml` (see `.env.example`):

```
METRICS_PUSH_ENABLED=true
GRAFANA_CLOUD_PROMETHEUS_URL=https://prometheus-prod-xx-prod-eu-west-x.grafana.net/api/prom/push
GRAFANA_CLOUD_PROMETHEUS_USER=123456
GRAFANA_CLOUD_LOKI_URL=https://logs-prod-xxx.grafana.net/loki/api/v1/push
GRAFANA_CLOUD_LOKI_USER=654321
GRAFANA_CLOUD_API_TOKEN=glc_...
```

The token is a secret: keep it with the other server secrets, never in the repository.
Provisioning (#21) and deploys (#23) place the `.env`; the agent is part of the `prod`
profile, so `docker compose --profile prod up -d` starts it with everything else.

### 3. Caddy

The Caddyfile (#21) needs to expose metrics on an internal port and log requests as JSON
to stdout:

```caddyfile
{
	metrics
}

# Internal only: not published by compose, scraped by alloy at caddy:2019.
:2019 {
	metrics /metrics
}

example.com {
	log {
		output stdout
		format json
	}
	reverse_proxy frontend:3000
}
```

Don't publish port 2019 in compose.

## Checking it works

- On the server: `docker compose --profile prod logs alloy` shows only warnings or errors;
  an empty log means it is shipping.
- In Grafana Cloud **Explore**:
  - Metrics: `up{environment="prod"}` lists `nhl-api`, `host`, `containers`, `caddy` with value 1.
  - Data job: `ingestion_last_success_seconds` (pushed every minute when ingestion runs).
  - Logs: `{environment="prod", service="backend-api"}`; errors only: add `level="error"`.

## Local testing

The agent is not started by a plain `docker compose up`. To try it locally without
Grafana Cloud, `compose.local-test.yaml` runs the `prod` profile with a local Prometheus
and Loki in their place, under its own project name and ports. The commands to start,
inspect and clean up are at the top of that file.

## Resource use

Alloy is capped at 300 MB (`mem_limit`) and typically uses 100–150 MB. Scrapes run every
60 s and the data job pushes every minute.
