#!/bin/sh
set -eu

# This test catches a backup script that fails to export the database, files,
# or configuration, or that deletes directories outside its own backup naming rule.
SCRIPT_PATH="$(cd "$(dirname "$0")/.." && pwd)/backup-qpo.sh"

if [ ! -f "$SCRIPT_PATH" ]; then
  echo "Expected Linux backup script was not found: $SCRIPT_PATH" >&2
  exit 1
fi

TEMP_ROOT="$(mktemp -d)"
cleanup() {
  rm -rf "$TEMP_ROOT"
}
trap cleanup EXIT INT TERM

PROJECT_DIR="$TEMP_ROOT/qpo-offline-package-test"
BACKUP_ROOT="$TEMP_ROOT/backups"
FAKE_BIN="$TEMP_ROOT/fake-bin"
FAKE_UPLOADS="$TEMP_ROOT/source-uploads"
FAKE_DB_DUMP="$TEMP_ROOT/postgres.sql"
FAKE_DOCKER_LOG="$TEMP_ROOT/fake-docker.log"

mkdir -p "$PROJECT_DIR/scripts" "$BACKUP_ROOT" "$FAKE_BIN" "$FAKE_UPLOADS"
printf 'name,drawing\nfixture,fixture-drawing.pdf\n' > "$FAKE_UPLOADS/upload-index.csv"
printf 'CREATE TABLE backup_fixture (id integer);\nINSERT INTO backup_fixture VALUES (1);\n' > "$FAKE_DB_DUMP"

cat > "$PROJECT_DIR/docker-compose.yml" <<'EOF'
name: quote-system
services: {}
EOF

cat > "$PROJECT_DIR/erp.env" <<'EOF'
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=not-for-production
ERP_FILE_STORAGE_TYPE=LOCAL
EOF

cat > "$FAKE_BIN/docker" <<'EOF'
#!/bin/sh
set -eu

printf '%s\n' "$*" >> "$FAKE_DOCKER_LOG"
[ "$1" = "compose" ] || exit 2
shift

action=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file|-f)
      shift 2
      ;;
    exec|cp)
      action="$1"
      shift
      break
      ;;
    *)
      shift
      ;;
  esac
done

case "$action" in
  exec)
    [ "$1" = "-T" ] && shift
    service="$1"
    shift
    [ "$service" = "postgres" ] || exit 3
    [ "$1" = "pg_dump" ] || exit 4
    cat "$FAKE_DB_DUMP"
    ;;
  cp)
    source="$1"
    destination="$2"
    [ "$source" = "backend:/data/uploads/." ] || exit 5
    mkdir -p "$destination"
    cp "$FAKE_UPLOADS/upload-index.csv" "$destination/upload-index.csv"
    ;;
  *)
    exit 6
    ;;
esac
EOF
chmod +x "$FAKE_BIN/docker"

mkdir -p "$BACKUP_ROOT/qpo-backup-20260101_010101"
touch -t 202001010101.01 "$BACKUP_ROOT/qpo-backup-20260101_010101"
touch "$BACKUP_ROOT/qpo-backup-20260101_010101/.completed"
mkdir -p "$BACKUP_ROOT/not-a-qpo-backup"
touch -t 202001010101.01 "$BACKUP_ROOT/not-a-qpo-backup"

PATH="$FAKE_BIN:$PATH" \
FAKE_UPLOADS="$FAKE_UPLOADS" \
FAKE_DB_DUMP="$FAKE_DB_DUMP" \
FAKE_DOCKER_LOG="$FAKE_DOCKER_LOG" \
sh "$SCRIPT_PATH" \
  --project-dir "$PROJECT_DIR" \
  --backup-root "$BACKUP_ROOT" \
  --keep-days 30 \
  --keep-count 1

completed_backup_count="$(find "$BACKUP_ROOT" -maxdepth 2 -name .completed -type f | wc -l | tr -d ' ')"
[ "$completed_backup_count" = "1" ]
[ ! -d "$BACKUP_ROOT/qpo-backup-20260101_010101" ]
[ -d "$BACKUP_ROOT/not-a-qpo-backup" ]

NEW_BACKUP_DIR="$(find "$BACKUP_ROOT" -maxdepth 1 -type d -name 'qpo-backup-*' | head -n 1)"
[ -n "$NEW_BACKUP_DIR" ]
[ -f "$NEW_BACKUP_DIR/db/postgres.sql.gz" ]
[ -f "$NEW_BACKUP_DIR/uploads/upload-index.csv" ]
[ -f "$NEW_BACKUP_DIR/config/erp.env" ]
[ -f "$NEW_BACKUP_DIR/meta/backup.json" ]
[ -f "$NEW_BACKUP_DIR/meta/sha256sums.txt" ]

gzip -dc "$NEW_BACKUP_DIR/db/postgres.sql.gz" | grep -F "CREATE TABLE backup_fixture" >/dev/null
cmp "$FAKE_UPLOADS/upload-index.csv" "$NEW_BACKUP_DIR/uploads/upload-index.csv"
grep -F '"storageType": "LOCAL"' "$NEW_BACKUP_DIR/meta/backup.json" >/dev/null

echo "backup-qpo.test.sh passed"
