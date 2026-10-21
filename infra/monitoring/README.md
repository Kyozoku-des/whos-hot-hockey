# Grafana Cloud monitoring (Terraform)

The agent in `monitoring/` ships metrics and logs to Grafana Cloud;
this module sets up what you see and get notified about there:

- **Alert rules** (`alerting.tf`), sent by email
- **Uptime checks** from outside the server (`uptime.tf`), once the site has a domain
- **Overview dashboard** (`dashboards/overview.json.tftpl`): traffic, API, server,
  containers, data ingestion and error logs
- All in a **Who's Hot Hockey** folder

It is its own Terraform root module with its own local state, separate from the server
provisioning planned in #21.

## Alerts

| Alert | Severity | Fires when |
|---|---|---|
| Site down | critical | an uptime check fails from most probe locations (needs `site_url`) |
| Server silent | critical | no metrics from the server for 10 min: server, network or agent down |
| Scrape target down | critical | the agent can't reach `host`, `containers`, `whos-hot-hockey-api` or `caddy` for 5 min |
| Service missing | critical | `caddy`, `frontend`, `backend-api`, `data-job` or `postgres` not running for 5 min |
| Data job silent | critical | the data job hasn't pushed metrics for 15 min |
| Container restarting | warning | a service restarted more than once in 15 min (a deploy restarts each once) |
| Container out of memory | warning | a service was OOM-killed |
| Server CPU high | warning | CPU above 90% for 15 min |
| Server memory low | warning | under 10% memory available for 10 min |
| Server disk low | warning | under 15% disk left (ext4/xfs/btrfs only) |
| API errors | warning | over 5% of API requests return 5xx, with some traffic |
| Ingestion stale | warning | no complete `players-full` or `team-schedules` run in 6 hours |
| NHL API failing | warning | over 20% of NHL API requests fail for 10 min |
| Error logs | warning | over 20 error log entries in 15 min |

A dead server sends one "Server silent" alert, not one per rule: threshold rules treat
missing data as OK, and "Service missing" and "Data job silent" only count while the
data they depend on still arrives. "Site down" needs most probe locations to fail, so
one location's network trouble doesn't page. Thresholds are in `alerting.tf`.

## Setup

Terraform 1.6+ is needed. Without a local install, run it through Docker from this
folder (PowerShell):

```powershell
docker run --rm -it -v "${PWD}:/work" -w /work hashicorp/terraform:1.16.4 init
```

and use the same prefix for `plan` and `apply`.

1. **Service account token:** in Grafana, **Administration → Users and access → Service
   accounts**, create one with the **Editor** role and add a token.
2. **Uptime checks (optional for now):** in **Testing & synthetics → Synthetics → Config**,
   note the API URL and create an access token.
3. Copy `terraform.tfvars.example` to `terraform.tfvars` (git-ignored) and fill it in.
   Leave `site_url` empty until the site has a domain; everything else works without it.
4. `terraform init`, `terraform plan`, then `terraform apply`.

The data source UIDs default to Grafana Cloud's hosted ones (`grafanacloud-prom`,
`grafanacloud-logs`). If a plan or the dashboard shows "data source not found", look up
the real UIDs under **Connections → Data sources** and set `prometheus_datasource_uid`
and `loki_datasource_uid`.

## State

State is local (`terraform.tfstate`, git-ignored) and contains the tokens. Back it up
privately (e.g. as a Bitwarden attachment) together with `terraform.tfvars`. If it is
lost, the resources can be imported again or deleted in Grafana and recreated.

`grafana_notification_policy` manages the stack's whole notification policy tree: it
replaces any policy set up by hand.

## Changing things

Edit the files and run `terraform apply`. Changes made in the Grafana UI to these
resources are overwritten on the next apply. The dashboard is read-only in the UI for
that reason; to rework it, edit it in Grafana, export the JSON and bring the changes back
into `dashboards/overview.json.tftpl`.

### Testing alert rules

`tests/alerts.test.yml` holds promtool scenarios for the alerts: a dead server sends
only "Server silent", one failing probe location doesn't page, a deploy's restart
doesn't count as a crash loop, and so on. `tests/run-alert-tests.sh` renders the rules
from Terraform exactly as they are applied and runs the scenarios (needs only Docker;
on Windows run it from Git Bash):

```bash
infra/monitoring/tests/run-alert-tests.sh
```

When changing a rule or threshold, add or adjust a scenario. Loki rules (Error logs)
are not covered: promtool only evaluates PromQL.

CI (`.github/workflows/terraform.yml`) checks formatting, validates the module and runs
the alert tests on pull requests. Nothing is applied from CI.
