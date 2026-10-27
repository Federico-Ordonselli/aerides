# Two container apps in the same environment, like the two containers of docker compose:
# - frontend: nginx with the Angular build, the only public entry point;
# - backend: Spring Boot, reachable only from inside the environment (internal ingress).
# nginx forwards /api and /ws to the backend, so the browser sees one origin.

locals {
  backend_name  = "${var.name_prefix}-backend"
  frontend_name = "${var.name_prefix}-frontend"
  frontend_url  = "https://${azurerm_container_app.frontend.ingress[0].fqdn}"

  # The backend's own database role: rows only, no DDL. Flyway creates it, as the administrator, at startup
  # (AppRoleProvisioner), so Terraform needs no connection to the database.
  app_db_role = "raidboard_app"

  # Container Apps refuses empty secrets, so the optional ones exist only when set. for_each can't depend
  # on a sensitive value: nonsensitive() reveals only "set or not", never the value itself.
  has_rawg_api_key  = nonsensitive(var.rawg_api_key != "")
  has_smtp_password = nonsensitive(var.smtp_password != "")

  # A private registry needs credentials; public images don't
  registries = nonsensitive(var.registry_password == null) ? [] : [{
    server   = split("/", var.image_repository)[0]
    username = var.registry_username
  }]
}

resource "azurerm_container_app" "backend" {
  name                         = local.backend_name
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  tags                         = var.tags

  # Secrets are stored by Container Apps and passed to the container as environment variables
  secret {
    name  = "db-password"
    value = var.postgres_admin_password
  }
  secret {
    name  = "db-app-password"
    value = var.postgres_app_password
  }
  secret {
    name  = "jwt-secret"
    value = var.jwt_secret
  }
  dynamic "secret" {
    for_each = local.has_rawg_api_key ? ["rawg-api-key"] : []
    content {
      name  = secret.value
      value = var.rawg_api_key
    }
  }
  dynamic "secret" {
    for_each = local.has_smtp_password ? ["smtp-password"] : []
    content {
      name  = secret.value
      value = var.smtp_password
    }
  }
  dynamic "secret" {
    for_each = local.registries
    content {
      name  = "registry-password"
      value = var.registry_password
    }
  }

  dynamic "registry" {
    for_each = local.registries
    content {
      server               = registry.value.server
      username             = registry.value.username
      password_secret_name = "registry-password"
    }
  }

  ingress {
    external_enabled = false # internal: only nginx in the same environment can call it
    target_port      = 8080
    transport        = "auto" # HTTP/1.1 with WebSocket upgrade for /ws
    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    # Exactly one replica: the STOMP broker, the rate-limit buckets and the WebSocket token registry are in
    # memory. More replicas would need a broker relay (RabbitMQ) and a shared store (Redis) first.
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "backend"
      image  = "${var.image_repository}-backend:${var.image_tag}"
      cpu    = 0.5
      memory = "1Gi"

      env {
        name = "DB_URL"
        # verify-full: encrypted AND the server certificate and host name are checked, so nobody in between
        # can pose as the database. DefaultJavaSSLFactory trusts the JRE's CA store, which has the root of
        # Azure's certificate chain (DigiCert Global Root G2): no certificate file to ship in the image.
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${azurerm_postgresql_flexible_server_database.raidboard.name}?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory"
      }
      # Two database users: the connection pool uses the application role, Flyway the administrator, who
      # owns the schema. A separate Flyway user is also what turns on AppRoleProvisioner.
      env {
        name  = "DB_USERNAME"
        value = local.app_db_role
      }
      env {
        name        = "DB_PASSWORD"
        secret_name = "db-app-password"
      }
      env {
        name  = "SPRING_FLYWAY_USER"
        value = azurerm_postgresql_flexible_server.main.administrator_login
      }
      env {
        name        = "SPRING_FLYWAY_PASSWORD"
        secret_name = "db-password"
      }
      env {
        name        = "JWT_SECRET"
        secret_name = "jwt-secret"
      }
      dynamic "env" {
        for_each = local.has_rawg_api_key ? ["rawg-api-key"] : []
        content {
          name        = "RAWG_API_KEY"
          secret_name = env.value
        }
      }
      env {
        name  = "CORS_ALLOWED_ORIGINS"
        value = local.frontend_url
      }
      env {
        name  = "APP_URL"
        value = local.frontend_url
      }
      env {
        name  = "MAIL_HOST"
        value = var.smtp.host
      }
      env {
        name  = "MAIL_PORT"
        value = tostring(var.smtp.port)
      }
      env {
        name  = "MAIL_USERNAME"
        value = var.smtp.username
      }
      dynamic "env" {
        for_each = local.has_smtp_password ? ["smtp-password"] : []
        content {
          name        = "MAIL_PASSWORD"
          secret_name = env.value
        }
      }
      env {
        name  = "MAIL_FROM"
        value = var.smtp.from
      }

      # Same probes as docker compose: Spring Boot's own liveness and readiness groups
      startup_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/liveness"
        interval_seconds        = 5
        failure_count_threshold = 24 # up to 2 minutes for the first start (Flyway migrations)
      }
      liveness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/liveness"
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/readiness"
      }
    }
  }
}

resource "azurerm_container_app" "frontend" {
  name                         = local.frontend_name
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  tags                         = var.tags

  dynamic "secret" {
    for_each = local.registries
    content {
      name  = "registry-password"
      value = var.registry_password
    }
  }

  dynamic "registry" {
    for_each = local.registries
    content {
      server               = registry.value.server
      username             = registry.value.username
      password_secret_name = "registry-password"
    }
  }

  ingress {
    external_enabled = true # the public entry point, with a managed HTTPS certificate
    target_port      = 80
    transport        = "auto"
    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas = 1
    max_replicas = 2 # static files and a proxy: nothing in memory, so it can scale

    container {
      name   = "frontend"
      image  = "${var.image_repository}-frontend:${var.image_tag}"
      cpu    = 0.25
      memory = "0.5Gi"

      # Read by the nginx template (frontend/nginx.conf.template). Inside the environment the backend is
      # reached by app name on port 80, and the internal ingress routes by Host, so nginx sends the
      # backend's own host name instead of the browser's.
      env {
        name  = "BACKEND_URL"
        value = "http://${local.backend_name}"
      }
      env {
        name  = "BACKEND_HOST"
        value = local.backend_name
      }
      # nginx is not the edge here: the Container Apps ingress is, so $remote_addr would be the ingress for
      # every user, and everybody would share one rate-limit bucket. nginx appends to the ingress's
      # X-Forwarded-For instead, and Tomcat takes the right-most address that isn't a private network
      # (server.tomcat.remoteip.internal-proxies default): the client IP the ingress wrote. Anything a
      # client injects further left is ignored. HTTPS ends at the ingress, so its X-Forwarded-Proto is kept.
      env {
        name  = "FORWARDED_FOR"
        value = "$proxy_add_x_forwarded_for"
      }
      env {
        name  = "FORWARDED_PROTO"
        value = "$http_x_forwarded_proto"
      }
    }
  }
}
