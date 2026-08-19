#!/bin/sh
set -eu

# This test catches destructive restore flows that skip confirmation, checksum
# validation, database restore, or uploaded-file restore.
SCRIPT_PATH="$(cd "$(dirname "$0")/.." && pwd)/restore-qpo.sh"

if [ ! -f "$SCRIPT_PATH" ]; then
  echo "Expected Linux restore script was not found: $SCRIPT_PATH" >&2
  exit 1
fi

TEMP_ROOT="$(mktemp -d)"
cleanup() {
  rm -rf "$TEMP_ROOT"
}
trap cleanup EXIT INT TERM

PROJECT_DIR="$TEMP_ROOT/qpo-offline-package-test"
BACKUP_DIR="$TEMP_ROOT/backups/qpo-backup-20260819_020000"
FAKE_BIN="$TEMP_ROOT/fake-bin"
FAKE_DOCKER_LOG="$TEMP_ROOT/fake-docker.log"

mkdir -p "$PROJECT_DIR" "$BACKUP_DIR/db" "$BACKUP_DIR/uploads" "$BACKUP_DIR/config" "$BACKUP_DIR/meta" "$FAKE_BIN"

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

printf 'CREATE TABLE restore_fixture (id integer);\n' | gzip -c > "$BACKUP_DIR/db/postgres.sql.gz"
printf 'fixture-drawing\n' > "$BACKUP_DIR/uploads/drawing.txt"
cat > "$BACKUP_DIR/meta/backup.json" <<'EOF'
{
  "database": "postgres",
  "databaseUser": "postgres",
  "storageType": "LOCAL",
  "fileBackup": "uploads"
}
EOF
(
  cd "$BACKUP_DIR"
  sha256sum db/postgres.sql.gz config/erp.env docker-compose.yml 2>/dev/null || true
  sha256sum db/postgres.sql.gz
  sha256sum uploads/drawing.txt
) > "$BACKUP_DIR/meta/sha256sums.txt"
touch "$BACKUP_DIR/.completed"

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
    stop|up|exec|cp|ps)
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
    stop|up|ps)
    exit 0
    ;;
  exec)
    [ "$1" = "-T" ] && shift
    service="$1"
    shift
    case "$service" in
      postgres|backend)
        exit 0
        ;;
      *)
        exit 3
        ;;
    esac
    ;;
  cp)
    source="$1"
    destination="$2"
    case "$destination" in
      postgres:/tmp/*)
        exit 0
        ;;
      backend:/data/uploads)
        exit 0
        ;;
      *)
        exit 4
        ;;
    esac
    ;;
  *)
    exit 5
    ;;
esac
EOF
chmod +x "$FAKE_BIN/docker"

if PATH="$FAKE_BIN:$PATH" \
  FAKE_DOCKER_LOG="$FAKE_DOCKER_LOG" \
  sh "$SCRIPT_PATH" \
    --project-dir "$PROJECT_DIR" \
    --backup-dir "$BACKUP_DIR" >/dev/null 2>&1; then
  echo "Restore must require --force." >&2
  exit 1
fi

PATH="$FAKE_BIN:$PATH" \
FAKE_DOCKER_LOG="$FAKE_DOCKER_LOG" \
sh "$SCRIPT_PATH" \
  --project-dir "$PROJECT_DIR" \
  --backup-dir "$BACKUP_DIR" \
  --force

grep -F "stop backend frontend" "$FAKE_DOCKER_LOG" >/dev/null
grep -F "up -d postgres" "$FAKE_DOCKER_LOG" >/dev/null
grep -F "up -d backend frontend" "$FAKE_DOCKER_LOG" >/dev/null
grep -F "psql" "$FAKE_DOCKER_LOG" >/dev/null
grep -F "backend:/data/uploads" "$FAKE_DOCKER_LOG" >/dev/null
if grep -F "down -v" "$FAKE_DOCKER_LOG" >/dev/null; then
  echo "Restore must never delete Docker volumes with down -v." >&2
  exit 1
fi

echo "restore-qpo.test.sh passed"
