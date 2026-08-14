#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
source "$ROOT/scripts/harness/common.sh"

frontend_files="$(frontend_changed_files)"
go_files="$(go_changed_files)"

if [ -n "$frontend_files" ]; then
  echo "== Frontend fast verification =="
  (
    cd "$ROOT/frontend"
    rel_files="$(printf '%s\n' "$frontend_files" | sed 's#^frontend/##')"
    npx oxfmt $rel_files
    npx oxlint $rel_files --no-error-on-unmatched-pattern
  )
else
  echo "== Frontend fast verification skipped: no changed frontend JS/TS files =="
fi

if [ -n "$go_files" ]; then
  echo "== Backend fast verification =="
  printf '%s\n' "$go_files" | xargs gofmt -w
  packages="$(go_changed_packages)"
  if [ -n "$packages" ]; then
    (
      cd "$ROOT/backend-go"
      GOCACHE="$(go_cache_dir)" go vet $packages
    )
  fi
else
  echo "== Backend fast verification skipped: no changed Go files =="
fi
