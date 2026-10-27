terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }

  # State is local by default. For a team, keep it in an Azure Storage container instead
  # (created once by hand), so it is shared and locked during apply:
  #
  # backend "azurerm" {
  #   resource_group_name  = "raidboard-tfstate"
  #   storage_account_name = "raidboardtfstate"
  #   container_name       = "tfstate"
  #   key                  = "raidboard.tfstate"
  # }
}

provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}
