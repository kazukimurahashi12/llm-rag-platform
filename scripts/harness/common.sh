#!/usr/bin/env bash
set -euo pipefail

repo_root() {
  git rev-parse --show-toplevel
}

go_cache_dir() {
  local root
  root="$(repo_root)"
  mkdir -p "$root/.cache/go-build"
  printf '%s\n' "$root/.cache/go-build"
}

changed_files() {
  git status --porcelain=v1 | sed -E 's/^...//' | sed -E 's/^.* -> //'
}

frontend_changed_files() {
  changed_files | grep -E '^frontend/.*\.(js|jsx|ts|tsx)$|^frontend/vite\.config\.ts$' || true
}

go_changed_files() {
  changed_files | grep -E '^backend-go/.*\.go$' | grep -v '^backend-go/internal/api/types\.gen\.go$' || true
}

go_changed_packages() {
  go_changed_files | xargs -n1 dirname 2>/dev/null | sort -u | sed 's#^backend-go#.#' || true
}
