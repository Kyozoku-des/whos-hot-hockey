output "ipv4_address" {
  description = "Server IPv4 address"
  value       = hcloud_server.app.ipv4_address
}

output "ipv6_address" {
  description = "Server IPv6 address"
  value       = hcloud_server.app.ipv6_address
}

output "ssh_admin" {
  description = "Admin login"
  value       = "ssh ${var.admin_user}@${hcloud_server.app.ipv4_address}"
}

output "nameservers" {
  description = "Set these as the domain's nameservers at the registrar"
  value       = var.domain == "" ? [] : hcloud_zone.site[0].authoritative_nameservers.assigned
}
