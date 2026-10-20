# Uptime checks from public probe locations outside the server: a server that is
# down can't report it. Created only once site_url is set.

data "grafana_synthetic_monitoring_probes" "all" {
  count = var.site_url == "" ? 0 : 1
}

locals {
  probe_ids = var.site_url == "" ? [] : [
    for location in var.probe_locations : data.grafana_synthetic_monitoring_probes.all[0].probes[location]
  ]

  uptime_checks = { for name, target in {
    site = "${var.site_url}/"
    # A cheap API call through Caddy and nginx: checks the whole request path.
    api = "${var.site_url}/api/teams/standings"
  } : name => target if var.site_url != "" }
}

resource "grafana_synthetic_monitoring_check" "uptime" {
  for_each = local.uptime_checks

  job       = "whos-hot-hockey-${each.key}"
  target    = each.value
  enabled   = true
  probes    = local.probe_ids
  frequency = 60000
  timeout   = 10000

  labels = {
    environment = "prod"
  }

  settings {
    http {
      method             = "GET"
      valid_status_codes = [200]
      fail_if_not_ssl    = true
    }
  }
}
