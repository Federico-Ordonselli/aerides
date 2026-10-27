variable "subscription_id" {
  description = "Azure subscription to deploy into."
  type        = string
}

variable "location" {
  description = "Azure region for every resource."
  type        = string
  default     = "italynorth"
}

variable "name_prefix" {
  description = "Prefix of every resource name, e.g. raidboard-prod-backend."
  type        = string
  default     = "raidboard-prod"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,20}$", var.name_prefix))
    error_message = "Lowercase letters, digits and dashes, 3 to 21 characters, starting with a letter."
  }
}

variable "image_repository" {
  description = "Container registry path of the images built by the Docker workflow: <repository>-backend and <repository>-frontend."
  type        = string
  default     = "ghcr.io/federico-ordonselli/raidboard"
}

variable "image_tag" {
  description = "Image tag to deploy. Prefer a commit SHA (sha-<40 chars>) over latest: a deployment should be reproducible."
  type        = string
  default     = "latest"
}

variable "registry_username" {
  description = "User for a private registry. Leave null for public images."
  type        = string
  default     = null
}

variable "registry_password" {
  description = "Token for a private registry (for GHCR: a token with read:packages)."
  type        = string
  default     = null
  sensitive   = true
}

variable "postgres_admin_password" {
  description = "Password of the PostgreSQL administrator, also used by the backend."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.postgres_admin_password) >= 16
    error_message = "Use at least 16 characters."
  }
}

variable "jwt_secret" {
  description = "Secret that signs the access tokens (JWT_SECRET), at least 32 characters."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.jwt_secret) >= 32
    error_message = "The backend refuses to start with a secret shorter than 32 characters."
  }
}

variable "rawg_api_key" {
  description = "RAWG API key for the game search. Empty: game search answers 503, everything else works."
  type        = string
  default     = ""
  sensitive   = true
}

variable "smtp" {
  description = "Mail server for the emails (e.g. Azure Communication Services or any SMTP relay). Without it, emails fail and are retried, nothing else breaks."
  type = object({
    host     = string
    port     = number
    username = string
    from     = string
  })
  default = {
    host     = "localhost"
    port     = 25
    username = ""
    from     = "Raidboard <noreply@raidboard.local>"
  }
}

variable "smtp_password" {
  description = "Password of the SMTP user."
  type        = string
  default     = ""
  sensitive   = true
}

variable "tags" {
  description = "Tags added to every resource."
  type        = map(string)
  default = {
    project    = "raidboard"
    managed-by = "terraform"
  }
}
