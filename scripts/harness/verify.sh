#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
source "$ROOT/scripts/harness/common.sh"

echo "== Frontend strong verification =="
(
  cd "$ROOT/frontend"
  npm run format:check
  npm run lint
  npm run lint:typecheck
  npm run build
)

echo "== Agent runtime verification =="
(
  cd "$ROOT/agent-runtime"
  npm run build
)

echo "== Backend strong verification =="
(
  cd "$ROOT/backend-go"
  unformatted="$(gofmt -l .)"
  if [ -n "$unformatted" ]; then
    echo "gofmt check failed:"
    printf '%s\n' "$unformatted"
    exit 1
  fi

  GOCACHE="$(go_cache_dir)" go vet ./...

  if command -v golangci-lint >/dev/null 2>&1; then
    set +e
    golangci_output="$(GOCACHE="$(go_cache_dir)" golangci-lint run ./... 2>&1)"
    golangci_status=$?
    set -e
    if [ "$golangci_status" -ne 0 ]; then
      if printf '%s\n' "$golangci_output" | grep -q 'Go language version .* lower than the targeted Go version'; then
        if [ "${HARNESS_STRICT_GOLANGCI:-0}" = "1" ]; then
          printf '%s\n' "$golangci_output"
          exit "$golangci_status"
        fi
        echo "WARNING: golangci-lint skipped locally because its build Go version is older than backend-go/go.mod."
        echo "Set HARNESS_STRICT_GOLANGCI=1 or update golangci-lint/Go to enforce this gate locally."
      else
        printf '%s\n' "$golangci_output"
        exit "$golangci_status"
      fi
    fi
  else
    if [ "${HARNESS_STRICT_GOLANGCI:-0}" = "1" ]; then
      echo "golangci-lint is required when HARNESS_STRICT_GOLANGCI=1"
      exit 1
    fi
    echo "WARNING: golangci-lint not found; skipping local lint gate."
  fi

  GOCACHE="$(go_cache_dir)" go test ./...
)
