output "app_url" {
  description = "Public address of the application."
  value       = local.frontend_url
}

output "postgres_fqdn" {
  description = "Host name of the PostgreSQL server."
  value       = azurerm_postgresql_flexible_server.main.fqdn
}

output "resource_group" {
  description = "Resource group that contains everything: delete it to remove the whole deployment."
  value       = azurerm_resource_group.main.name
}
