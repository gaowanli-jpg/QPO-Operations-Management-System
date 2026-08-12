# QPO Flyway migration policy

## Why this exists

The QPO database was compressed to a clean Flyway baseline after the order data cleanup window. The active migration directory now starts from:

- `V1__qpo_baseline_schema.sql`
- `V2__qpo_baseline_seed_data.sql`

Some local and test databases may still have old compressed-history records such as `V3/V4` in `flyway_schema_history`. Those numbers are reserved history and must not be reused. Reusing them can make Flyway skip a new migration during Docker Compose startup, then the backend starts with code that expects tables or columns that were never created.

## Rule

- Keep `V1/V2` as the compressed baseline.
- Treat `V3/V4` as reserved compressed-history versions.
- New migrations start at `V5` or later.
- Always allocate the next migration number from both sources: active repository files and the running database `flyway_schema_history`.

## Creating a migration

Use the helper instead of manually choosing a number:

```powershell
./scripts/new-flyway-migration.ps1 -Name u9_customer_sync_schedule_config
```

The script reads `quote-backend/src/main/resources/db/migration` and, when `quote-postgres` is running, reads `flyway_schema_history`. It creates the next safe `Vx__name.sql` above both maxima.

## Starting Docker Compose

Use the guarded startup helper during development:

```powershell
./scripts/compose-up.ps1
```

This helper uses `erp.env`, runs `scripts/check-flyway-migrations.ps1`, starts PostgreSQL first, checks the live Flyway history, and only then builds and starts backend/frontend. If a migration would be skipped because the database is already ahead of the repository, the script stops before the backend becomes unhealthy.

## Direct checks

For CI or manual diagnosis:

```powershell
./scripts/check-flyway-migrations.ps1
```

The check fails on duplicate migration versions, any active migration that reuses `V3/V4`, or a running database whose successful Flyway version is higher than the repository's active maximum.

## Existing databases with old V3/V4 history

Clean Docker images only contain the active migration files. Existing development databases can still have old compressed-history rows for `V3/V4`. The backend includes a guarded Flyway migration strategy that repairs only those reserved missing rows before running `migrate()`. Missing rows for any version outside `V3/V4` remain fatal, because that would mean a real migration file was removed or a deployment package is incomplete.
