# One VPS running the whole stack under Docker Compose.
#
# Terraform only creates the machine. cloud-init installs Docker and the deploy script;
# the application itself is deployed by CI. There are no database backups,
# so the server must never be replaced: see the lifecycle block.

resource "hcloud_ssh_key" "admin" {
  name       = "${var.server_name}-admin"
  public_key = var.admin_ssh_public_key
}

resource "hcloud_firewall" "server" {
  name = var.server_name

  rule {
    description = "SSH"
    direction   = "in"
    protocol    = "tcp"
    port        = "22"
    source_ips  = var.ssh_allowed_cidrs
  }

  rule {
    description = "HTTP (ACME challenges and redirect to HTTPS)"
    direction   = "in"
    protocol    = "tcp"
    port        = "80"
    source_ips  = ["0.0.0.0/0", "::/0"]
  }

  rule {
    description = "HTTPS"
    direction   = "in"
    protocol    = "tcp"
    port        = "443"
    source_ips  = ["0.0.0.0/0", "::/0"]
  }

  rule {
    description = "HTTP/3"
    direction   = "in"
    protocol    = "udp"
    port        = "443"
    source_ips  = ["0.0.0.0/0", "::/0"]
  }

  rule {
    description = "Ping"
    direction   = "in"
    protocol    = "icmp"
    source_ips  = ["0.0.0.0/0", "::/0"]
  }
}

resource "hcloud_server" "app" {
  name         = var.server_name
  server_type  = var.server_type
  image        = var.image
  location     = var.location
  ssh_keys     = [hcloud_ssh_key.admin.id]
  firewall_ids = [hcloud_firewall.server.id]

  user_data = templatefile("${path.module}/cloud-init.yaml.tftpl", {
    admin_user             = var.admin_user
    admin_ssh_public_key   = trimspace(var.admin_ssh_public_key)
    deploy_ssh_public_key  = trimspace(var.deploy_ssh_public_key)
    deploy_script          = base64encode(file("${path.module}/files/whoshot-deploy"))
    unattended_reboot_time = var.unattended_reboot_time
  })

  public_net {
    ipv4_enabled = true
    ipv6_enabled = true
  }

  # Also blocks deleting or rebuilding the server from the Hetzner console.
  delete_protection  = true
  rebuild_protection = true

  lifecycle {
    # Replacing the server wipes Postgres. cloud-init only runs on first boot, so
    # changes to it (and to the key or image) must not force a new server either.
    prevent_destroy = true
    ignore_changes  = [user_data, ssh_keys, image]
  }
}
