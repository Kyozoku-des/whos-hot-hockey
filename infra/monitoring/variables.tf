variable "grafana_url" {
  description = "Grafana Cloud stack URL, e.g. https://whoshothockey.grafana.net"
  type        = string
}

variable "grafana_token" {
  description = "Grafana service account token (Editor role is enough for folders, dashboards and alerting)"
  type        = string
  sensitive   = true
}

variable "alert_email" {
  description = "Email address that receives alerts"
  type        = string
}

variable "prometheus_datasource_uid" {
  description = "UID of the stack's hosted Prometheus data source"
  type        = string
  default     = "grafanacloud-prom"
}

variable "loki_datasource_uid" {
  description = "UID of the stack's hosted Loki data source"
  type        = string
  default     = "grafanacloud-logs"
}

variable "compose_project" {
  description = "Compose project name on the server; container metrics and logs carry it as the project label"
  type        = string
  default     = "whos-hot-nhl"
}

variable "expected_services" {
  description = "Compose services that must always be running on the server"
  type        = list(string)
  default     = ["caddy", "frontend", "backend-api", "data-job", "postgres"]
}

variable "scrape_jobs" {
  description = "Scrape jobs whose up series must be 1 (the data job pushes and has none)"
  type        = list(string)
  default     = ["host", "containers", "nhl-api", "caddy"]
}

# --- Uptime checks (optional until the site has a domain) ---------------------------

variable "site_url" {
  description = "Public site URL, e.g. https://whoshothockey.com. Leave empty to skip the uptime checks"
  type        = string
  default     = ""

  validation {
    condition     = var.site_url == "" || can(regex("^https://[^/]+$", var.site_url))
    error_message = "site_url must be https://<host> with no path or trailing slash, or empty."
  }
}

variable "sm_url" {
  description = "Synthetic monitoring API URL for the stack's region (Testing & synthetics > Synthetics > Config)"
  type        = string
  default     = ""
}

variable "sm_access_token" {
  description = "Synthetic monitoring access token (same page as sm_url)"
  type        = string
  default     = ""
  sensitive   = true
}

variable "probe_locations" {
  description = "Public probe locations that run the uptime checks"
  type        = list(string)
  default     = ["Frankfurt", "London", "NorthVirginia"]
}
