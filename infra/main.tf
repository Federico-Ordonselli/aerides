# Shared pieces: the resource group, the logs and the Container Apps environment that hosts both apps.

resource "azurerm_resource_group" "main" {
  name     = "${var.name_prefix}-rg"
  location = var.location
  tags     = var.tags
}

# Container Apps send the containers' stdout/stderr here: the Spring Boot and nginx logs
resource "azurerm_log_analytics_workspace" "main" {
  name                = "${var.name_prefix}-logs"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = var.tags
}

# Apps in the same environment reach each other by name over its internal network
resource "azurerm_container_app_environment" "main" {
  name                       = "${var.name_prefix}-env"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  tags                       = var.tags
}
