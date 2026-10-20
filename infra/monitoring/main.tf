resource "grafana_folder" "whos_hot_hockey" {
  title = "Who's Hot Hockey"
}

# Renamed with the repository; keeps the existing folder (and what is in it) instead of
# destroying and recreating it.
moved {
  from = grafana_folder.whos_hot_nhl
  to   = grafana_folder.whos_hot_hockey
}

resource "grafana_dashboard" "overview" {
  folder = grafana_folder.whos_hot_hockey.uid
  config_json = templatefile("${path.module}/dashboards/overview.json.tftpl", {
    prometheus_uid  = var.prometheus_datasource_uid
    loki_uid        = var.loki_datasource_uid
    compose_project = var.compose_project
  })
}
