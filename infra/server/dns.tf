# DNS in Hetzner (optional). Buy the domain at any registrar and set its nameservers to
# the ones in the `nameservers` output.

locals {
  dns_records = var.domain == "" ? {} : {
    "@/A"      = { name = "@", type = "A", value = hcloud_server.app.ipv4_address }
    "@/AAAA"   = { name = "@", type = "AAAA", value = hcloud_server.app.ipv6_address }
    "www/A"    = { name = "www", type = "A", value = hcloud_server.app.ipv4_address }
    "www/AAAA" = { name = "www", type = "AAAA", value = hcloud_server.app.ipv6_address }
  }
}

resource "hcloud_zone" "site" {
  count = var.domain == "" ? 0 : 1

  name              = var.domain
  mode              = "primary"
  delete_protection = true
}

resource "hcloud_zone_rrset" "site" {
  for_each = local.dns_records

  zone    = hcloud_zone.site[0].name
  name    = each.value.name
  type    = each.value.type
  ttl     = 300
  records = [{ value = each.value.value }]
}
