#!/usr/bin/env sh
set -eu

PROJECT_DIR=""
BACKUP_DIR=""
ENV_FILE=""
COMPOSE_FILE=""
FORCE=false

usage() {
  cat <<'EOF'
Usage:
  sh scripts/restore-qpo.sh --backup-dir <path> --force [options]

Required:
  --backup-dir <path>   Backup directory created by backup-qpo.sh.
  --force               Confirm that the target database and file storage may be overwritten.

Options:
  --project-dir <path>  QPO offline-package directory. Defaults to the script parent directory.
  --env-file <path>     Current server environment file. Defaults to <project-dir>/erp.env.
  --compose-file <path> Current Compose file. Defaults to <project-dir>/docker-compose.yml.
  --help                Show this help.

The restore keeps the current erp.env and Docker volumes. It replaces only the
database schema and the backed-up file storage after checksum verification.
EOF
}

die() {
  echo "QPO restore failed: $*" >&2
  exit 1
}

read_env_value() {
  key="$1"
  file="$2"
  awk -v key="$key" '
    /^[[:space:]]*#/ || !/=/{ next }
    {
      line = $0
      sub(/^[[:space:]]*/, "", line)
      if (index(line, key "=") == 1) {
        value = substr(line, length(key) + 2)
        sub(/\r$/, "", value)
        sub(/^"/, "", value)
        sub(/"$/, "", value)
        print value
        exit
      }
    }
  ' "$file"
}

read_json_value() {
  key="$1"
  file="$2"
  awk -v key="$key" '
    {
      pattern = "\"" key "\"[[:space:]]*:[[:space:]]*\""
      if ($0 ~ pattern) {
        value = $0
        sub(".*\"" key "\"[[:space:]]*:[[:space:]]*\"", "", value)
        sub("\"[[:space:]]*,?[[:space:]]*$", "", value)
        print value
        exit
      }
    }
  ' "$file"
}

run_compose() {
  if [ "${STORAGE_TYPE:-LOCAL}" = "MINIO" ]; then
    (
      cd "$PROJECT_DIR"
      docker compose --profile minio --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
    )
  else
    (
      cd "$PROJECT_DIR"
      docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
    )
  fi
}

wait_for_postgres() {
  attempts=0
  while [ "$attempts" -lt 60 ]; do
    if run_compose exec -T postgres pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
      return
    fi
    attempts=$((attempts + 1))
    sleep 1
  done
  die "PostgreSQL did not become ready within 60 seconds."
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --project-dir)
      [ "$#" -ge 2 ] || die "--project-dir requires a value."
      PROJECT_DIR="$2"
      shift 2
      ;;
    --backup-dir)
      [ "$#" -ge 2 ] || die "--backup-dir requires a value."
      BACKUP_DIR="$2"
      shift 2
      ;;
    --env-file)
      [ "$#" -ge 2 ] || die "--env-file requires a value."
      ENV_FILE="$2"
      shift 2
      ;;
    --compose-file)
      [ "$#" -ge 2 ] || die "--compose-file requires a value."
      COMPOSE_FILE="$2"
      shift 2
      ;;
    --force)
      FORCE=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      die "Unknown option: $1"
      ;;
  esac
done

[ "$FORCE" = true ] || die "Restore is destructive. Re-run with --force after confirming the target may be overwritten."
[ -n "$BACKUP_DIR" ] || die "--backup-dir is required."

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -z "$PROJECT_DIR" ]; then
  PROJECT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
else
  PROJECT_DIR="$(CDPATH= cd -- "$PROJECT_DIR" && pwd)"
fi

if [ -z "$ENV_FILE" ]; then
  ENV_FILE="$PROJECT_DIR/erp.env"
fi
if [ -z "$COMPOSE_FILE" ]; then
  COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"
fi
BACKUP_DIR="$(CDPATH= cd -- "$BACKUP_DIR" && pwd)"

[ -f "$ENV_FILE" ] || die "Environment file not found: $ENV_FILE"
[ -f "$COMPOSE_FILE" ] || die "Compose file not found: $COMPOSE_FILE"
[ -f "$BACKUP_DIR/.completed" ] || die "Backup is not marked complete: $BACKUP_DIR"
[ -f "$BACKUP_DIR/meta/backup.json" ] || die "Backup metadata not found."
[ -f "$BACKUP_DIR/meta/sha256sums.txt" ] || die "Backup checksum file not found."
[ -f "$BACKUP_DIR/db/postgres.sql.gz" ] || die "Compressed PostgreSQL dump not found."
command -v docker >/dev/null 2>&1 || die "docker command was not found."
command -v sha256sum >/dev/null 2>&1 || die "sha256sum command was not found."

(
  cd "$BACKUP_DIR"
  sha256sum -c "meta/sha256sums.txt"
) || die "Backup checksum verification failed."

DB_USER="$(read_env_value POSTGRES_USER "$ENV_FILE")"
DB_NAME="$(read_env_value POSTGRES_DB "$ENV_FILE")"
DB_USER="${DB_USER:-postgres}"
DB_NAME="${DB_NAME:-postgres}"

metadata_db_user="$(read_json_value databaseUser "$BACKUP_DIR/meta/backup.json")"
metadata_db_name="$(read_json_value database "$BACKUP_DIR/meta/backup.json")"
STORAGE_TYPE="$(read_json_value storageType "$BACKUP_DIR/meta/backup.json")"
FILE_BACKUP="$(read_json_value fileBackup "$BACKUP_DIR/meta/backup.json")"

DB_USER="${metadata_db_user:-$DB_USER}"
DB_NAME="${metadata_db_name:-$DB_NAME}"
STORAGE_TYPE="$(printf '%s' "${STORAGE_TYPE:-LOCAL}" | tr '[:lower:]' '[:upper:]')"
FILE_BACKUP="${FILE_BACKUP:-uploads}"

case "$STORAGE_TYPE" in
  LOCAL)
    [ "$FILE_BACKUP" = "uploads" ] || die "Unsupported LOCAL backup path: $FILE_BACKUP"
    [ -d "$BACKUP_DIR/uploads" ] || die "Uploaded-file backup directory not found."
    ;;
  MINIO)
    [ "$FILE_BACKUP" = "minio" ] || die "Unsupported MINIO backup path: $FILE_BACKUP"
    [ -d "$BACKUP_DIR/minio" ] || die "MinIO backup directory not found."
    ;;
  *)
    die "Unsupported storage type in backup metadata: $STORAGE_TYPE"
    ;;
esac

echo "Verified backup: $BACKUP_DIR"
echo "Database: $DB_NAME"
echo "Storage: $STORAGE_TYPE"
echo "The current erp.env will be preserved."

run_compose stop backend frontend
if [ "$STORAGE_TYPE" = "MINIO" ]; then
  run_compose up -d postgres minio
else
  run_compose up -d postgres
fi
wait_for_postgres

container_dump="/tmp/qpo-restore-$$.sql.gz"
run_compose cp "$BACKUP_DIR/db/postgres.sql.gz" "postgres:$container_dump"
run_compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
  -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"

restore_command="gzip -dc '$container_dump' | psql -v ON_ERROR_STOP=1 -U '$DB_USER' -d '$DB_NAME'"
run_compose exec -T postgres sh -lc "$restore_command"
run_compose exec -T postgres rm -f "$container_dump"

run_compose up -d backend frontend

if [ "$STORAGE_TYPE" = "LOCAL" ]; then
  run_compose exec -T backend sh -lc \
    "rm -rf /data/uploads/* /data/uploads/.[!.]* /data/uploads/..?* 2>/dev/null || true"
  run_compose cp "$BACKUP_DIR/uploads/." "backend:/data/uploads"
else
  run_compose exec -T minio sh -lc \
    "rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null || true"
  run_compose cp "$BACKUP_DIR/minio/." "minio:/data"
fi

run_compose ps
echo "QPO restore completed: $BACKUP_DIR"
