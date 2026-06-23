#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

DOKKA_TASK="${DOKKA_TASK:-:dokkaGenerate}"
DOKKA_OUTPUT_DIR="${DOKKA_OUTPUT_DIR:-build/dokka/html}"
BASE_URL="${BASE_URL:-/lucene-kmp/}"
GRADLE_ARGS="${GRADLE_ARGS:-}"
DOKKA_FAST_EXCLUDES="${DOKKA_FAST_EXCLUDES:-true}"

if [ ! -d docs ]; then
  echo "docs/ does not exist." >&2
  exit 1
fi

if [ ! -f docs/package.json ]; then
  echo "docs/package.json is missing. Doks/Thulite site is not set up correctly." >&2
  exit 1
fi

if [ ! -d docs/node_modules ]; then
  if [ -f docs/package-lock.json ]; then
    (cd docs && npm ci)
  else
    (cd docs && npm install)
  fi
fi

read -r -a GRADLE_ARGS_ARRAY <<< "$GRADLE_ARGS"
DOKKA_EXCLUDE_ARGS=()

if [ "$DOKKA_FAST_EXCLUDES" != "false" ]; then
  echo "Calculating Dokka task exclusions for non-common Kotlin source sets..."
  mapfile -t DOKKA_EXCLUDED_TASKS < <(
    ./gradlew "${GRADLE_ARGS_ARRAY[@]}" "$DOKKA_TASK" --dry-run --console=plain |
      awk '
        /^:/ {
          task=$1
          sub(/ .*/, "", task)
          if (task ~ /(commonizeNativeDistribution|downloadKotlinNativeDistribution|transformNativeMain|compileNativeMainKotlinMetadata|metadataNativeMain|transformPosixNativeMain|compilePosixNativeMainKotlinMetadata|metadataPosixNativeMain|[A-Za-z0-9]+MetadataElements)$/) {
            print task
          }
        }
      ' |
      sort -u
  )

  for task in "${DOKKA_EXCLUDED_TASKS[@]}"; do
    DOKKA_EXCLUDE_ARGS+=("-x" "$task")
  done

  echo "Excluded ${#DOKKA_EXCLUDED_TASKS[@]} native/target metadata tasks from Dokka generation."
fi

./gradlew "${GRADLE_ARGS_ARRAY[@]}" "${DOKKA_EXCLUDE_ARGS[@]}" "$DOKKA_TASK"

if [ ! -f "$DOKKA_OUTPUT_DIR/index.html" ]; then
  echo "Dokka output not found at $DOKKA_OUTPUT_DIR/index.html" >&2
  echo "Searching for generated Dokka index.html files..." >&2
  find . -path '*/build/dokka/*/index.html' -not -path './docs/node_modules/*' -not -path './docs/public/*' -print >&2 || true
  exit 1
fi

rm -rf docs/api
mkdir -p docs/api
cp -R "$DOKKA_OUTPUT_DIR"/. docs/api/
node scripts/fix-dokka-broken-links.mjs docs/api

cd "$ROOT_DIR/docs"
npm run build -- --baseURL "$BASE_URL"
cd "$ROOT_DIR"

rm -rf docs/public/api
mkdir -p docs/public/api
cp -R docs/api/. docs/public/api/
touch docs/public/.nojekyll

if [ ! -f docs/public/index.html ]; then
  echo "docs/public/index.html is missing after site build." >&2
  exit 1
fi

if [ ! -f docs/api/index.html ]; then
  echo "docs/api/index.html is missing after Dokka copy." >&2
  exit 1
fi

if [ ! -f docs/public/api/index.html ]; then
  echo "docs/public/api/index.html is missing after Dokka copy into published site." >&2
  exit 1
fi

echo "Docs site built successfully: docs/public"
echo "Local Dokka API docs: docs/api"
echo "Published Dokka API docs: docs/public/api"
