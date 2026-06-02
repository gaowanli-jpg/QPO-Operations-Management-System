# Production Deployment Notes

## Local Docker verification
```powershell
cd D:\Code
docker compose up -d --build
```

Default local URLs:
- Frontend: `http://localhost:8088`
- Backend health check: `http://localhost:8080/api/health`

## Environment file
Copy `.env.example` to `.env` on the deployment machine and change the production values:

```powershell
Copy-Item .env.example .env
notepad .env
```

Important values:
- `POSTGRES_PASSWORD`: must be filled before startup. Use a long random password.
- `ERP_JWT_SECRET`: must be filled before startup. Use a strong random value with at least 32 characters; the backend refuses to start with the old default value or a short secret.
- `ERP_CORS_ALLOWED_ORIGINS`: set to the real frontend origin, for example `https://erp.example.com`.
- `POSTGRES_BIND`: keep `127.0.0.1` unless a separate database server or trusted internal network requires otherwise.
- `BACKEND_BIND`: keep `127.0.0.1`; the frontend container reaches the backend through Docker's internal network.
- `FRONTEND_BIND`: use `127.0.0.1` when an external Nginx on the same server proxies HTTPS traffic; use `0.0.0.0` only when exposing the frontend container directly.
- `FRONTEND_PORT`: set to `8088` behind an external Nginx proxy, or `80` only if no external reverse proxy is used.
- `DB_POOL_MAX_SIZE`: backend database connection pool size. Keep the default `15` for one backend container; avoid setting it very high unless PostgreSQL `max_connections` and server capacity are checked.
- `DB_POOL_MIN_IDLE`: idle database connections kept ready. Default `3` is enough for normal internal use.
- `ERP_TASK_CORE_POOL_SIZE` / `ERP_TASK_MAX_POOL_SIZE`: background task thread pool for long-running jobs such as quote refresh and large exports. Keep the defaults `2` / `4` unless CPU and database capacity are checked.

Connection pool rule of thumb:

```text
backend container count * DB_POOL_MAX_SIZE + other database clients < PostgreSQL max_connections
```

Generate secrets on Windows PowerShell:

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

Generate secrets on Linux:

```bash
openssl rand -base64 48
```

The Compose file also enables container health checks, log rotation, conservative CPU/memory limits, and `no-new-privileges` for application containers.

## HTTPS reverse proxy
Use `deploy/nginx/quote-system.https.example.conf` as the server-level Nginx example.

Before production:
1. Replace `erp.example.com` with the real domain.
2. Replace certificate paths with the real certificate and private key.
3. Keep Docker Compose frontend exposed on `127.0.0.1:8088` or the server firewall restricted port.
4. Set `.env`:

```env
ERP_CORS_ALLOWED_ORIGINS=https://erp.example.com
POSTGRES_BIND=127.0.0.1
BACKEND_BIND=127.0.0.1
FRONTEND_BIND=127.0.0.1
FRONTEND_PORT=8088
```

After changing `.env`, recreate containers:

```powershell
docker compose up -d --build
```

Check health after startup:

```powershell
docker compose ps
curl http://localhost:8080/api/health
```
