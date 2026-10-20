#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT=""
OUTPUT_DIR=""
IMAGE_TAG="${IMAGE_TAG:-}"
INCLUDE_MINIO="${INCLUDE_MINIO:-false}"
SKIP_DEPENDENCY_PULL="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --image-tag)
      IMAGE_TAG="$2"
      shift 2
      ;;
    --include-minio)
      INCLUDE_MINIO="true"
      shift
      ;;
    --skip-dependency-pull)
      SKIP_DEPENDENCY_PULL="true"
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$PROJECT_ROOT" ]]; then
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
else
  PROJECT_ROOT="$(cd "$PROJECT_ROOT" && pwd)"
fi

sanitize_image_tag() {
  local tag="$1"
  if [[ -z "${tag// }" ]]; then
    tag="latest"
  fi
  tag="$(printf '%s' "$tag" | sed -E 's/[^A-Za-z0-9_.-]+/-/g; s/^[.-]+//; s/[.-]+$//' | cut -c1-120)"
  if [[ -z "$tag" ]]; then
    tag="latest"
  fi
  printf '%s' "$tag"
}

resolve_default_image_tag() {
  local exact_tag=""
  if exact_tag="$(git -C "$PROJECT_ROOT" describe --tags --exact-match HEAD 2>/dev/null)" && [[ -n "$exact_tag" ]]; then
    printf '%s' "$exact_tag"
    return
  fi

  local branch=""
  if branch="$(git -C "$PROJECT_ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null)" && [[ -n "$branch" ]]; then
    if [[ "$branch" == "master" || "$branch" == "main" ]]; then
      printf '%s' "$branch"
      return
    fi
  fi

  local short_sha=""
  if short_sha="$(git -C "$PROJECT_ROOT" rev-parse --short=12 HEAD 2>/dev/null)" && [[ -n "$short_sha" ]]; then
    printf '%s' "$short_sha"
    return
  fi

  printf '%s' "local"
}

run_checked() {
  echo "+ $*"
  "$@"
}

image_exists() {
  docker image inspect "$1" >/dev/null 2>&1
}

ensure_image_available() {
  local image="$1"
  if [[ "$SKIP_DEPENDENCY_PULL" == "true" ]]; then
    if ! image_exists "$image"; then
      echo "Docker image is required but not available locally: $image" >&2
      exit 1
    fi
    echo "Using local dependency image: $image"
    return
  fi

  echo "+ docker pull $image"
  if docker pull "$image"; then
    return
  fi

  if image_exists "$image"; then
    echo "Warning: failed to pull $image, using the local copy instead." >&2
    return
  fi

  echo "Failed to pull required Docker image and no local copy exists: $image" >&2
  exit 1
}

if [[ -z "$IMAGE_TAG" ]]; then
  IMAGE_TAG="$(resolve_default_image_tag)"
fi
IMAGE_TAG="$(sanitize_image_tag "$IMAGE_TAG")"

if [[ -z "$OUTPUT_DIR" ]]; then
  OUTPUT_DIR="$PROJECT_ROOT/dist/qpo-offline-package-$IMAGE_TAG"
fi
mkdir -p "$OUTPUT_DIR/scripts"

backend_image="qpo-backend:$IMAGE_TAG"
frontend_image="qpo-frontend:$IMAGE_TAG"
postgres_image="postgres:15"
minio_image="minio/minio:latest"

cd "$PROJECT_ROOT"
run_checked docker version
run_checked docker build -t "$backend_image" "$PROJECT_ROOT/quote-backend"
run_checked docker build -t "$frontend_image" "$PROJECT_ROOT/quote-frontend"

ensure_image_available "$postgres_image"
if [[ "$INCLUDE_MINIO" == "true" ]]; then
  ensure_image_available "$minio_image"
fi

images=("$backend_image" "$frontend_image" "$postgres_image")
if [[ "$INCLUDE_MINIO" == "true" ]]; then
  images+=("$minio_image")
fi

archive_path="$OUTPUT_DIR/qpo-offline-images-$IMAGE_TAG.tar"
rm -f "$archive_path"
run_checked docker save -o "$archive_path" "${images[@]}"

cp "$PROJECT_ROOT/docker-compose.yml" "$OUTPUT_DIR/docker-compose.yml"
cp "$PROJECT_ROOT/erp.env.example" "$OUTPUT_DIR/erp.env.example"
cp "$PROJECT_ROOT/QPO部署说明.md" "$OUTPUT_DIR/QPO部署说明.md"
cp "$PROJECT_ROOT/scripts/load-offline-images.ps1" "$OUTPUT_DIR/scripts/load-offline-images.ps1"
cp "$PROJECT_ROOT/scripts/load-offline-images.sh" "$OUTPUT_DIR/scripts/load-offline-images.sh"
cp "$PROJECT_ROOT/scripts/backup-qpo.sh" "$OUTPUT_DIR/scripts/backup-qpo.sh"
cp "$PROJECT_ROOT/scripts/restore-qpo.sh" "$OUTPUT_DIR/scripts/restore-qpo.sh"
cp "$PROJECT_ROOT/QPO服务器备份说明.md" "$OUTPUT_DIR/QPO服务器备份说明.md"
cp "$PROJECT_ROOT/QPO服务器恢复说明.md" "$OUTPUT_DIR/QPO服务器恢复说明.md"

cat > "$OUTPUT_DIR/qpo-image.env" <<EOF
QPO_IMAGE_TAG=$IMAGE_TAG
QPO_BACKEND_IMAGE=$backend_image
QPO_FRONTEND_IMAGE=$frontend_image
QPO_IMAGE_ARCHIVE=$(basename "$archive_path")
EOF

{
  printf '{\n'
  printf '  "imageTag": "%s",\n' "$IMAGE_TAG"
  printf '  "createdAt": "%s",\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  printf '  "archive": "%s",\n' "$(basename "$archive_path")"
  printf '  "includeMinio": %s,\n' "$(if [[ "$INCLUDE_MINIO" == "true" ]]; then echo true; else echo false; fi)"
  printf '  "images": ['
  for index in "${!images[@]}"; do
    if [[ "$index" != "0" ]]; then
      printf ', '
    fi
    printf '"%s"' "${images[$index]}"
  done
  printf ']\n}\n'
} > "$OUTPUT_DIR/qpo-offline-package-manifest.json"

echo "Offline image package created: $OUTPUT_DIR"
echo "Image archive: $archive_path"
