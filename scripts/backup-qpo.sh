#!/usr/bin/env sh
set -eu

PROJECT_DIR=""
BACKUP_ROOT=""
ENV_FILE=""
COMPOSE_FILE=""
KEEP_DAYS=30
KEEP_COUNT=30
TEMP_DIR=""
LOCK_DIR=""
LOCK_HELD=false

usage() {
  cat <<'EOF'
Usage:
  sh scripts/backup-qpo.sh [options]

Options:
  --project-dir <path>   QPO offline-package directory. Defaults to the script parent directory.
  --backup-root <path>   Backup destination. Defaults to the parent directory's backups directory.
  --env-file <path>      Environment file. Defaults to <project-dir>/erp.env.
  --compose-file <path>  Compose file. Defaults to <project-dir>/docker-compose.yml.
  --keep-days <days>     Delete completed QPO backups older than this many days. 0 disables it.
  --keep-count <count>   Retain at most this many completed QPO backups. 0 disables it.
  --help                 Show this help.

The backup contains a compressed PostgreSQL logical dump, file storage, a protected erp.env
copy, compose/image metadata, and checksums. It never deletes Docker volumes or business data.
EOF
}

die() {
  echo "QPO backup failed: $*" >&2
  exit 1
}

require_non_negative_integer() {
  case "$2" in
    ''|*[!0-9]*)
      die "$1 must be a non-negative integer."
      ;;
  esac
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

run_compose() {
  (
    cd "$PROJECT_DIR"
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  )
}

cleanup() {
  status=$?
  if [ -n "$TEMP_DIR" ] && [ -d "$TEMP_DIR" ]; then
    rm -rf "$TEMP_DIR"
  fi
  if [ "$LOCK_HELD" = true ] && [ -n "$LOCK_DIR" ] && [ -d "$LOCK_DIR" ]; then
    rm -rf "$LOCK_DIR"
  fi
  trap - EXIT INT TERM
  exit "$status"
}

acquire_lock() {
  LOCK_DIR="$BACKUP_ROOT/.qpo-backup.lock"
  if mkdir "$LOCK_DIR" 2>/dev/null; then
    LOCK_HELD=true
    printf '%s\n' "$$" > "$LOCK_DIR/pid"
    return
  fi

  if [ -f "$LOCK_DIR/pid" ]; then
    stale_pid="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
    case "$stale_pid" in
      ''|*[!0-9]*)
        ;;
      *)
        if kill -0 "$stale_pid" 2>/dev/null; then
          die "Another QPO backup is already running (pid=$stale_pid)."
        fi
        ;;
    esac
  fi

  rm -rf "$LOCK_DIR"
  mkdir "$LOCK_DIR" 2>/dev/null || die "Unable to acquire backup lock: $LOCK_DIR"
  LOCK_HELD=true
  printf '%s\n' "$$" > "$LOCK_DIR/pid"
}

prune_old_backups() {
  if [ "$KEEP_DAYS" -gt 0 ]; then
    find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name 'qpo-backup-*' -mtime "+$KEEP_DAYS" -print |
      while IFS= read -r backup_dir; do
        if [ -f "$backup_dir/.completed" ]; then
          rm -rf "$backup_dir"
          echo "Removed expired QPO backup: $backup_dir"
        fi
      done
  fi

  if [ "$KEEP_COUNT" -gt 0 ]; then
    backup_list="$BACKUP_ROOT/.qpo-backup-prune-$$.txt"
    find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name 'qpo-backup-*' -print |
      sort -r > "$backup_list"

    retained=0
    while IFS= read -r backup_dir; do
      [ -f "$backup_dir/.completed" ] || continue
      retained=$((retained + 1))
      if [ "$retained" -gt "$KEEP_COUNT" ]; then
        rm -rf "$backup_dir"
        echo "Removed surplus QPO backup: $backup_dir"
      fi
    done < "$backup_list"
    rm -f "$backup_list"
  fi
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --project-dir)
      [ "$#" -ge 2 ] || die "--project-dir requires a value."
      PROJECT_DIR="$2"
      shift 2
      ;;
    --backup-root)
      [ "$#" -ge 2 ] || die "--backup-root requires a value."
      BACKUP_ROOT="$2"
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
    --keep-days)
      [ "$#" -ge 2 ] || die "--keep-days requires a value."
      KEEP_DAYS="$2"
      shift 2
      ;;
    --keep-count)
      [ "$#" -ge 2 ] || die "--keep-count requires a value."
      KEEP_COUNT="$2"
      shift 2
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

require_non_negative_integer "--keep-days" "$KEEP_DAYS"
require_non_negative_integer "--keep-count" "$KEEP_COUNT"

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
if [ -z "$BACKUP_ROOT" ]; then
  BACKUP_ROOT="$(CDPATH= cd -- "$PROJECT_DIR/.." && pwd)/backups"
fi

[ -f "$ENV_FILE" ] || die "Environment file not found: $ENV_FILE"
[ -f "$COMPOSE_FILE" ] || die "Compose file not found: $COMPOSE_FILE"
command -v docker >/dev/null 2>&1 || die "docker command was not found."
command -v gzip >/dev/null 2>&1 || die "gzip command was not found."
command -v sha256sum >/dev/null 2>&1 || die "sha256sum command was not found."

mkdir -p "$BACKUP_ROOT"
BACKUP_ROOT="$(CDPATH= cd -- "$BACKUP_ROOT" && pwd)"
acquire_lock
trap 'cleanup' EXIT INT TERM

db_user="$(read_env_value POSTGRES_USER "$ENV_FILE")"
db_name="$(read_env_value POSTGRES_DB "$ENV_FILE")"
storage_type="$(read_env_value ERP_FILE_STORAGE_TYPE "$ENV_FILE")"
image_tag="$(read_env_value QPO_IMAGE_TAG "$ENV_FILE")"

db_user="${db_user:-postgres}"
db_name="${db_name:-postgres}"
storage_type="${storage_type:-LOCAL}"
storage_type="$(printf '%s' "$storage_type" | tr '[:lower:]' '[:upper:]')"

if [ -z "$image_tag" ] && [ -f "$PROJECT_DIR/qpo-image.env" ]; then
  image_tag="$(read_env_value QPO_IMAGE_TAG "$PROJECT_DIR/qpo-image.env")"
fi

timestamp="$(date '+%Y%m%d_%H%M%S')"
final_dir="$BACKUP_ROOT/qpo-backup-$timestamp"
TEMP_DIR="$BACKUP_ROOT/.qpo-backup-$timestamp-$$.tmp"
[ ! -e "$final_dir" ] || die "Backup directory already exists: $final_dir"

mkdir -p "$TEMP_DIR/db" "$TEMP_DIR/config" "$TEMP_DIR/meta"
chmod 700 "$TEMP_DIR"
cp "$ENV_FILE" "$TEMP_DIR/config/erp.env"
chmod 600 "$TEMP_DIR/config/erp.env"
cp "$COMPOSE_FILE" "$TEMP_DIR/config/docker-compose.yml"
chmod 600 "$TEMP_DIR/config/docker-compose.yml"
if [ -f "$PROJECT_DIR/qpo-image.env" ]; then
  cp "$PROJECT_DIR/qpo-image.env" "$TEMP_DIR/config/qpo-image.env"
  chmod 600 "$TEMP_DIR/config/qpo-image.env"
fi

raw_dump="$TEMP_DIR/db/postgres.sql"
echo "Exporting PostgreSQL database '$db_name'..."
run_compose exec -T postgres pg_dump -U "$db_user" -d "$db_name" > "$raw_dump"
[ -s "$raw_dump" ] || die "PostgreSQL dump is empty."
gzip -c "$raw_dump" > "$TEMP_DIR/db/postgres.sql.gz"
rm -f "$raw_dump"
[ -s "$TEMP_DIR/db/postgres.sql.gz" ] || die "Compressed PostgreSQL dump is empty."

case "$storage_type" in
  LOCAL)
    file_backup_path="uploads"
    mkdir -p "$TEMP_DIR/$file_backup_path"
    echo "Copying local uploaded files..."
    run_compose cp "backend:/data/uploads/." "$TEMP_DIR/$file_backup_path"
    ;;
  MINIO)
    file_backup_path="minio"
    mkdir -p "$TEMP_DIR/$file_backup_path"
    echo "Copying local MinIO object storage..."
    run_compose cp "minio:/data/." "$TEMP_DIR/$file_backup_path"
    ;;
  *)
    die "Unsupported ERP_FILE_STORAGE_TYPE='$storage_type'. Use LOCAL or a local Compose MINIO service."
    ;;
esac

backup_time="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
cat > "$TEMP_DIR/meta/backup.json" <<EOF
{
  "backupTime": "$backup_time",
  "database": "$db_name",
  "databaseUser": "$db_user",
  "databaseDump": "db/postgres.sql.gz",
  "storageType": "$storage_type",
  "fileBackup": "$file_backup_path",
  "environmentFile": "config/erp.env",
  "composeFile": "config/docker-compose.yml",
  "imageTag": "$image_tag"
}
EOF

(
  cd "$TEMP_DIR"
  sha256sum "db/postgres.sql.gz" "config/erp.env" "config/docker-compose.yml" "meta/backup.json"
  if [ -f "config/qpo-image.env" ]; then
    sha256sum "config/qpo-image.env"
  fi
  find "$file_backup_path" -type f -print | sort |
    while IFS= read -r file; do
      sha256sum "$file"
    done
) > "$TEMP_DIR/meta/sha256sums.txt"

touch "$TEMP_DIR/.completed"
mv "$TEMP_DIR" "$final_dir"
TEMP_DIR=""
prune_old_backups

echo "QPO backup completed: $final_dir"
