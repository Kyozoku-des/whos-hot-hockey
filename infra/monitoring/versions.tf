terraform {
  required_version = ">= 1.6"

  required_providers {
    grafana = {
      source  = "grafana/grafana"
      version = "~> 4.49"
    }
  }
}

provider "grafana" {
  url  = var.grafana_url
  auth = var.grafana_token

  # Only used by the uptime checks (synthetic monitoring).
  sm_url          = var.sm_url
  sm_access_token = var.sm_access_token
}
