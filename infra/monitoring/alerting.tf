# Where alerts go: one email contact point for everything.

resource "grafana_contact_point" "email" {
  name = "Who's Hot NHL email"

  email {
    addresses               = [var.alert_email]
    single_email            = true
    disable_resolve_message = false
  }
}

# The stack's notification policy tree (a singleton). Firing alerts are grouped per
# rule, and a still-firing alert is repeated every 4 hours.
resource "grafana_notification_policy" "default" {
  contact_point   = grafana_contact_point.email.name
  group_by        = ["grafana_folder", "alertname"]
  group_wait      = "30s"
  group_interval  = "5m"
  repeat_interval = "4h"
}

locals {
  prod = "environment=\"prod\""
  disk = "${local.prod}, fstype=~\"ext4|xfs|btrfs\""

  # Absence rules return 1 when their data is gone, which no_data_state can't
  # suppress. So every absence rule except "Server silent" only counts while the data
  # it depends on still arrives: a dead server then sends "Server silent" alone
  # instead of one alert per missing service.
  agent_sending      = "count(up{${local.prod}, job=\"host\"})"
  containers_scraped = "(max(up{${local.prod}, job=\"containers\"}) == 1)"

  # One absent_over_time per expected service, so a missing container is named. Only
  # while container metrics are being collected (else "Scrape target down" fires).
  services_missing_expr = "(${join(" or ", [
    for service in var.expected_services :
    "absent_over_time(container_last_seen{${local.prod}, project=\"${var.compose_project}\", service=\"${service}\"}[5m])"
  ])}) and on() ${local.containers_scraped}"

  # Every rule: one query (A) and a threshold on its last value. Queries return the
  # measured value; absence rules return 1 when data is missing and nothing
  # otherwise, which no_data_state = OK treats as healthy.
  base_rules = {
    # --- Critical: the site or a core part of it is down -----------------------------

    "Server silent" = {
      severity   = "critical"
      datasource = "prometheus"
      expr       = "absent_over_time(up{${local.prod}, job=\"host\"}[10m])"
      op         = "gt"
      threshold  = 0
      for        = "0s"
      summary    = "No metrics from the server for 10 minutes: the server, its network or the monitoring agent is down."
    }

    "Scrape target down" = {
      severity   = "critical"
      datasource = "prometheus"
      expr       = "max by (job) (1 - up{${local.prod}, job=~\"${join("|", var.scrape_jobs)}\"})"
      op         = "gt"
      threshold  = 0
      for        = "5m"
      summary    = "The agent can't reach {{ $labels.job }} for 5 minutes."
    }

    "Service missing" = {
      severity   = "critical"
      datasource = "prometheus"
      expr       = local.services_missing_expr
      op         = "gt"
      threshold  = 0
      for        = "0s"
      summary    = "The {{ $labels.service }} container hasn't been running for 5 minutes."
    }

    "Data job silent" = {
      severity   = "critical"
      datasource = "prometheus"
      # Only while the agent still sends: otherwise "Server silent" covers it.
      expr      = "absent_over_time(ingestion_last_success{${local.prod}}[15m]) and on() ${local.agent_sending}"
      op        = "gt"
      threshold = 0
      for       = "0s"
      summary   = "The data job hasn't pushed metrics for 15 minutes: it is down, stuck or can't reach the agent. Stats stop updating."
    }

    # --- Warning: degraded, needs a look ---------------------------------------------

    "Container restarting" = {
      severity   = "warning"
      datasource = "prometheus"
      # A deploy restarts each container once; more than that is a crash loop.
      expr      = "max by (service) (changes(container_start_time_seconds{${local.prod}, project=\"${var.compose_project}\"}[15m]))"
      op        = "gt"
      threshold = 1
      for       = "0s"
      summary   = "{{ $labels.service }} restarted more than once in 15 minutes."
    }

    "Container out of memory" = {
      severity   = "warning"
      datasource = "prometheus"
      expr       = "max by (service) (increase(container_oom_events_total{${local.prod}, project=\"${var.compose_project}\"}[15m]))"
      op         = "gt"
      threshold  = 0
      for        = "0s"
      summary    = "{{ $labels.service }} was killed for running out of memory."
    }

    "Server CPU high" = {
      severity   = "warning"
      datasource = "prometheus"
      expr       = "1 - avg(rate(node_cpu_seconds_total{${local.prod}, mode=\"idle\"}[5m]))"
      op         = "gt"
      threshold  = 0.9
      for        = "15m"
      summary    = "Server CPU above 90% for 15 minutes."
    }

    "Server memory low" = {
      severity   = "warning"
      datasource = "prometheus"
      expr       = "min(node_memory_MemAvailable_bytes{${local.prod}} / node_memory_MemTotal_bytes{${local.prod}})"
      op         = "lt"
      threshold  = 0.1
      for        = "10m"
      summary    = "Less than 10% of server memory available for 10 minutes."
    }

    "Server disk low" = {
      severity   = "warning"
      datasource = "prometheus"
      # Real disks only: read-only images (e.g. an iso9660 config drive) always read full.
      expr      = "min(node_filesystem_avail_bytes{${local.disk}} / node_filesystem_size_bytes{${local.disk}})"
      op        = "lt"
      threshold = 0.15
      for       = "15m"
      summary   = "Less than 15% disk space left on the server."
    }

    "API errors" = {
      severity   = "warning"
      datasource = "prometheus"
      # Share of API requests failing with 5xx, only once there is some traffic.
      expr      = "(sum(rate(http_server_requests_seconds_count{${local.prod}, job=\"nhl-api\", outcome=\"SERVER_ERROR\"}[10m])) / sum(rate(http_server_requests_seconds_count{${local.prod}, job=\"nhl-api\"}[10m]))) and on() (sum(rate(http_server_requests_seconds_count{${local.prod}, job=\"nhl-api\"}[10m])) > 0.02)"
      op        = "gt"
      threshold = 0.05
      for       = "5m"
      summary   = "More than 5% of API requests fail with a server error."
    }

    "Ingestion stale" = {
      severity   = "warning"
      datasource = "prometheus"
      # A run with deferred players during live games doesn't count as a success, so
      # allow for a full game night before alerting.
      expr      = "time() - max by (mode) (ingestion_last_success{${local.prod}, mode=~\"players-full|team-schedules\"})"
      op        = "gt"
      threshold = 21600
      for       = "0s"
      summary   = "No complete {{ $labels.mode }} ingestion run in 6 hours: stats may be out of date."
    }

    "NHL API failing" = {
      severity   = "warning"
      datasource = "prometheus"
      expr       = "(sum(rate(nhl_api_requests_milliseconds_count{${local.prod}, outcome=~\"transient|permanent\"}[15m])) / sum(rate(nhl_api_requests_milliseconds_count{${local.prod}}[15m]))) and on() (sum(rate(nhl_api_requests_milliseconds_count{${local.prod}}[15m])) > 0.01)"
      op         = "gt"
      threshold  = 0.2
      for        = "10m"
      summary    = "More than 20% of requests to the NHL API fail."
    }

    "Error logs" = {
      severity   = "warning"
      datasource = "loki"
      expr       = "sum(count_over_time({${local.prod}, project=\"${var.compose_project}\", level=\"error\"}[15m]))"
      op         = "gt"
      threshold  = 20
      for        = "0s"
      summary    = "More than 20 error log entries in 15 minutes. In Explore: {environment=\"prod\", level=\"error\"}"
    }
  }

  # Only when the uptime checks exist (uptime.tf).
  uptime_rules = { for name, rule in {
    "Site down" = {
      severity   = "critical"
      datasource = "prometheus"
      # Share of successful probes across all locations: one failing location alone
      # (0, 1, 1 -> 0.67) doesn't fire, most locations failing (0, 0, 1 -> 0.33) does.
      expr      = "avg by (job) (avg_over_time(probe_success{job=~\"whos-hot-nhl-(site|api)\"}[5m]))"
      op        = "lt"
      threshold = 0.5
      for       = "2m"
      summary   = "{{ $labels.job }} fails from most probe locations: the site is down for visitors."
    }
  } : name => rule if var.site_url != "" }

  rules = merge(local.base_rules, local.uptime_rules)

  datasource_uids = {
    prometheus = var.prometheus_datasource_uid
    loki       = var.loki_datasource_uid
  }
}

resource "grafana_rule_group" "production" {
  name             = "Production"
  folder_uid       = grafana_folder.whos_hot_nhl.uid
  interval_seconds = 60

  dynamic "rule" {
    for_each = local.rules

    content {
      name           = rule.key
      condition      = "C"
      for            = rule.value.for
      no_data_state  = "OK"
      exec_err_state = "Error"

      labels = {
        severity = rule.value.severity
      }

      annotations = {
        summary = rule.value.summary
      }

      data {
        ref_id         = "A"
        datasource_uid = local.datasource_uids[rule.value.datasource]

        relative_time_range {
          from = 900
          to   = 0
        }

        model = jsonencode(merge(
          { refId = "A", expr = rule.value.expr, instant = true, intervalMs = 60000, maxDataPoints = 43200 },
          rule.value.datasource == "loki" ? { queryType = "instant" } : {}
        ))
      }

      data {
        ref_id         = "B"
        datasource_uid = "__expr__"

        relative_time_range {
          from = 0
          to   = 0
        }

        model = jsonencode({
          refId      = "B"
          type       = "reduce"
          expression = "A"
          reducer    = "last"
        })
      }

      data {
        ref_id         = "C"
        datasource_uid = "__expr__"

        relative_time_range {
          from = 0
          to   = 0
        }

        model = jsonencode({
          refId      = "C"
          type       = "threshold"
          expression = "B"
          conditions = [{
            evaluator = { type = rule.value.op, params = [rule.value.threshold] }
          }]
        })
      }
    }
  }
}
