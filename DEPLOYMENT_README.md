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
- `POSTGRES_PASSWORD`: change before production.
- `ERP_JWT_SECRET`: change before production.
- `ERP_CORS_ALLOWED_ORIGINS`: set to the real frontend origin, for example `https://erp.example.com`.
- `POSTGRES_BIND`, `BACKEND_BIND`, `FRONTEND_BIND`: use `127.0.0.1` when an external Nginx on the same server proxies traffic.
- `FRONTEND_PORT`: set to `8088` behind an external Nginx proxy, or `80` only if no external reverse proxy is used.

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
