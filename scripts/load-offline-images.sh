#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT=""
ENV_FILE="erp.env"
IMAGE_ARCHIVE=""
IMAGE_TAG="${QPO_IMAGE_TAG:-}"
INCLUDE_MINIO="false"
NO_START="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="$2"
      shift 2
      ;;
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --image-archive)
      IMAGE_ARCHIVE="$2"
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
    --no-start)
      NO_START="true"
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

run_checked() {
  echo "+ $*"
  "$@"
}

read_packaged_image_tag() {
  local image_env_path="$PROJECT_ROOT/qpo-image.env"
  if [[ -f "$image_env_path" ]]; then
    grep -E '^QPO_IMAGE_TAG=' "$image_env_path" | head -n 1 | cut -d '=' -f 2- || true
  fi
}

cd "$PROJECT_ROOT"

if [[ -z "$IMAGE_ARCHIVE" ]]; then
  IMAGE_ARCHIVE="$(ls -t "$PROJECT_ROOT"/qpo-offline-images-*.tar 2>/dev/null | head -n 1 || true)"
  if [[ -z "$IMAGE_ARCHIVE" ]]; then
    echo "No qpo-offline-images-*.tar archive found in $PROJECT_ROOT" >&2
    exit 1
  fi
fi

run_checked docker load -i "$IMAGE_ARCHIVE"

if [[ -z "$IMAGE_TAG" ]]; then
  IMAGE_TAG="$(read_packaged_image_tag)"
fi
if [[ -z "$IMAGE_TAG" ]]; then
  archive_name="$(basename "$IMAGE_ARCHIVE")"
  if [[ "$archive_name" =~ ^qpo-offline-images-(.+)\.tar$ ]]; then
    IMAGE_TAG="${BASH_REMATCH[1]}"
  fi
fi
if [[ -z "$IMAGE_TAG" ]]; then
  IMAGE_TAG="latest"
fi
export QPO_IMAGE_TAG="$IMAGE_TAG"

if [[ "$NO_START" == "true" ]]; then
  echo "Images loaded. Skipped compose startup because --no-start was set."
  exit 0
fi

env_path="$PROJECT_ROOT/$ENV_FILE"
if [[ ! -f "$env_path" ]]; then
  if [[ -f "$PROJECT_ROOT/erp.env.example" ]]; then
    cp "$PROJECT_ROOT/erp.env.example" "$env_path"
    echo "Created $env_path from erp.env.example. Fill required secrets, then rerun this script." >&2
    exit 1
  fi
  echo "Compose env file not found: $env_path" >&2
  exit 1
fi

compose_args=(compose --env-file "$env_path")
if [[ "$INCLUDE_MINIO" == "true" ]]; then
  compose_args+=(--profile minio)
fi
compose_args+=(up -d --no-build)
run_checked docker "${compose_args[@]}"
run_checked docker compose --env-file "$env_path" ps
