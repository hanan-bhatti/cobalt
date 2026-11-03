#!/usr/bin/env bash
set -euo pipefail
cobalt_editor_script_dir="$(dirname -- "${BASH_SOURCE[0]}")"
cd -- "${cobalt_editor_script_dir}/.."
exec ./tools/check-notes-low-memory.sh \
  --project-cache-dir build/editor-validation/gradle-state \
  --init-script tools/editor-isolated-build.gradle \
  "$@"
