# PostgreSQL 17, Flexible Server. Flyway creates the schema when the backend starts.
#
# Known gaps, both required before real production use:
# - Network: the server has a public endpoint, and "Azure services" (the firewall rule below) means any
#   Azure customer's resources, not only ours: the password and TLS are what protect it. Restricting it to
#   our outbound IP isn't reliable either, because a Container Apps environment without a virtual network
#   has no guaranteed outbound address. The fix is a virtual network: a delegated subnet for the server, a
#   private DNS zone, the Container Apps environment in the same network, and public access disabled.
# - Database user: the backend connects as the administrator. It should use a role that owns only the
#   raidboard schema; creating it needs a PostgreSQL connection from Terraform (the postgresql provider),
#   which only works once the network above is in place.

resource "azurerm_postgresql_flexible_server" "main" {
  name                          = "${var.name_prefix}-db"
  location                      = azurerm_resource_group.main.location
  resource_group_name           = azurerm_resource_group.main.name
  version                       = "17"
  administrator_login           = "raidboard"
  administrator_password        = var.postgres_admin_password
  sku_name                      = "B_Standard_B1ms" # burstable, the cheapest tier: enough for one small group
  storage_mb                    = 32768
  backup_retention_days         = 7
  public_network_access_enabled = true
  tags                          = var.tags

  # Azure picks an availability zone; don't plan a change every time it reports which one
  lifecycle {
    ignore_changes = [zone]
  }
}

resource "azurerm_postgresql_flexible_server_database" "raidboard" {
  name      = "raidboard"
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# 0.0.0.0 - 0.0.0.0 is Azure's convention for "any Azure service", which includes Container Apps
resource "azurerm_postgresql_flexible_server_firewall_rule" "azure_services" {
  name             = "allow-azure-services"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}
