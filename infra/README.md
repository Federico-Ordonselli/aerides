# Raidboard on Azure (Terraform)

Infrastructure as code for a production-like deployment on Azure. **Written and validated, never applied**: CI runs `terraform fmt -check` and `terraform validate` on every change (`.github/workflows/infra.yml`), but nothing is deployed from here. The public demo runs on Render instead (see the main README).

## What it creates

```mermaid
flowchart LR
    user([Browser]) -->|HTTPS| fe
    subgraph env[Container Apps environment]
        fe["frontend<br/>nginx + Angular<br/>public ingress"]
        be["backend<br/>Spring Boot<br/>internal ingress, 1 replica"]
        fe -->|/api, /ws| be
    end
    be -->|JDBC, TLS| db[(PostgreSQL 17<br/>Flexible Server)]
    env -.->|logs| logs[Log Analytics]
    ghcr[[GHCR images]] -.-> fe & be
```

| File | Resources |
|------|-----------|
| `main.tf` | resource group, Log Analytics workspace, Container Apps environment |
| `database.tf` | PostgreSQL Flexible Server (B1ms, 32 GB, 7-day backups), the `raidboard` database, firewall rule for Azure services |
| `apps.tf` | `backend` and `frontend` container apps, their secrets, environment variables and health probes |
| `variables.tf` / `outputs.tf` | inputs (secrets are `sensitive`) and the public URL |

## Design choices

- **Same shape as docker compose**: nginx is the only public entry point and forwards `/api` and `/ws` to the backend, so the browser sees one origin (no CORS, `SameSite=Strict` refresh cookie). The backend has an *internal* ingress: nothing outside the environment can call it, which also keeps `X-Forwarded-For` trustworthy for the rate limiter.
- **The nginx upstream is configurable** (`frontend/nginx.conf.template`, filled in at container start): `http://backend:8080` in compose, the backend app's name here. The Container Apps ingress routes by `Host` header, so nginx sends the backend's host name instead of the browser's.
- **Exactly one backend replica**: the STOMP broker, the rate-limit buckets and the WebSocket token registry live in memory. Scaling out needs a broker relay (RabbitMQ) and a shared store (Redis) first; the frontend has no state and scales to two.
- **Secrets** (`jwt_secret`, database password, API keys) are `sensitive` variables, stored as Container Apps secrets and passed as environment variables, the same names the app reads everywhere else (`.env.example`). Optional secrets are created only when set, because Container Apps refuses empty ones.
- **Images by commit SHA**: the Docker workflow pushes `ghcr.io/<repo>-backend:sha-<commit>`; deploying an exact tag makes a deployment reproducible and a rollback a one-line change.
- **Known gap: private networking.** PostgreSQL has a public endpoint, limited to Azure services and with TLS required. The production setup puts the server in a delegated subnet with a private DNS zone, and the Container Apps environment in the same virtual network. It is left out to keep the configuration short, and noted in `database.tf`.

## Running it

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars   # fill in subscription, secrets, image tag
az login
terraform init
terraform plan -out=tfplan
terraform apply tfplan
terraform output app_url
```

Remove everything with `terraform destroy` (or by deleting the resource group). For a team, uncomment the `azurerm` backend in `versions.tf` so the state is shared and locked.

**Rough cost** (list prices, always on, may differ by region): PostgreSQL B1ms about €15/month, Container Apps 0.75 vCPU / 1.5 GiB about €25/month, Log Analytics a few euros at this volume.
