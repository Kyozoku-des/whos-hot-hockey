variable "hcloud_token" {
  description = "Hetzner Cloud API token (Read & Write) for the project"
  type        = string
  sensitive   = true
}

variable "server_name" {
  description = "Server name in Hetzner Cloud"
  type        = string
  default     = "whos-hot-hockey"
}

variable "server_type" {
  description = "Hetzner Cloud server type"
  type        = string
  default     = "cx23"
}

variable "location" {
  description = "Hetzner location: nbg1, fsn1 or hel1 (cx23 is EU only)"
  type        = string
  default     = "nbg1"
}

variable "image" {
  description = "OS image. Only used on creation; changing it does not rebuild the server"
  type        = string
  default     = "ubuntu-24.04"
}

variable "admin_user" {
  description = "Login for maintenance: sudo, key only"
  type        = string
  default     = "ops"
}

variable "admin_ssh_public_key" {
  description = "Public key for admin_user, e.g. the contents of ~/.ssh/id_ed25519.pub"
  type        = string
}

variable "deploy_ssh_public_key" {
  description = "Public half of the CI deploy key. It can only run the deploy script"
  type        = string
}

variable "ssh_allowed_cidrs" {
  description = "Sources allowed to reach SSH. CI deploys from GitHub runners, whose addresses change, so the default is everyone (login is key only)"
  type        = list(string)
  default     = ["0.0.0.0/0", "::/0"]
}

variable "domain" {
  description = "Site domain, e.g. whoshothockey.com, managed as a Hetzner DNS zone. Leave empty to skip DNS"
  type        = string
  default     = ""
}

variable "unattended_reboot_time" {
  description = "UTC time for a reboot after security updates that need one. Midday UTC is outside NHL game hours"
  type        = string
  default     = "12:00"
}
