resource "grafana_folder" "whos_hot_nhl" {
  title = "Who's Hot Hockey"
}

resource "grafana_dashboard" "overview" {
  folder = grafana_folder.whos_hot_nhl.uid
  config_json = templatefile("${path.module}/dashboards/overview.json.tftpl", {
    prometheus_uid  = var.prometheus_datasource_uid
    loki_uid        = var.loki_datasource_uid
    compose_project = var.compose_project
  })
}
