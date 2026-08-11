#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:8081}"
ADMIN_USERNAME="${AUDIT_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${AUDIT_ADMIN_PASSWORD:-change-me}"
RUN_ADVICE_SMOKE="${RUN_ADVICE_SMOKE:-false}"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

log() {
  printf '[go-only-smoke] %s\n' "$1"
}

request() {
  local method="$1"
  local path="$2"
  local output="$3"
  shift 3
  local status
  status="$(curl -sS -o "$output" -w '%{http_code}' -X "$method" "$API_BASE_URL$path" "$@")"
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    printf 'request failed: %s %s -> %s\n' "$method" "$path" "$status" >&2
    cat "$output" >&2
    exit 1
  fi
}

extract_json_string() {
  local key="$1"
  local file="$2"
  sed -E "s/.*\"$key\":\"([^\"]+)\".*/\1/" "$file"
}

health_file="$tmp_dir/health.json"
log "health"
request GET /health "$health_file"
grep -q '"status":"UP"' "$health_file"
grep -q '"db":"UP"' "$health_file"

auth_file="$tmp_dir/auth.json"
log "auth token"
request POST /v1/auth/token "$auth_file" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}"
ADMIN_TOKEN="$(extract_json_string accessToken "$auth_file")"
if [[ -z "$ADMIN_TOKEN" || "$ADMIN_TOKEN" == "$(cat "$auth_file")" ]]; then
  printf 'failed to extract accessToken\n' >&2
  cat "$auth_file" >&2
  exit 1
fi

auth_header=(-H "Authorization: Bearer $ADMIN_TOKEN")

dashboard_file="$tmp_dir/dashboard.json"
log "dashboard summary"
request GET /v1/dashboard/summary "$dashboard_file" "${auth_header[@]}"
grep -q '"totalAdviceRequests"' "$dashboard_file"

knowledge_list_file="$tmp_dir/knowledge-list.json"
log "knowledge list"
request GET '/v1/knowledge-documents?limit=5&offset=0' "$knowledge_list_file" "${auth_header[@]}"
grep -q '"items"' "$knowledge_list_file"

title="Smoke Knowledge $(date +%Y%m%d%H%M%S)"
knowledge_create_file="$tmp_dir/knowledge-create.json"
log "knowledge create"
request POST /v1/knowledge-documents "$knowledge_create_file" \
  "${auth_header[@]}" \
  -H 'Content-Type: application/json' \
  -d "{
    \"title\":\"$title\",
    \"content\":\"Smoke test document for Go-only backend verification. It covers onboarding, reporting, and 1on1 follow-up.\",
    \"accessScope\":\"SHARED\",
    \"allowedUsernames\":[],
    \"aceCategory\":\"EXPECTATION\"
  }"
grep -q "\"title\":\"$title\"" "$knowledge_create_file"

reindex_accept_file="$tmp_dir/reindex-accept.json"
log "reindex submit"
request POST /v1/knowledge-documents/reindex "$reindex_accept_file" "${auth_header[@]}"
grep -q '"jobId"' "$reindex_accept_file"

reindex_list_file="$tmp_dir/reindex-list.json"
log "reindex jobs"
request GET '/v1/knowledge-documents/reindex-jobs?limit=5&offset=0' "$reindex_list_file" "${auth_header[@]}"
grep -q '"items"' "$reindex_list_file"

audit_file="$tmp_dir/audit.json"
log "audit logs"
request GET '/v1/audit-logs?limit=5&offset=0' "$audit_file" "${auth_header[@]}"
grep -q '"items"' "$audit_file"

retrieval_file="$tmp_dir/retrieval.json"
log "retrieval evaluation"
request GET /v1/retrieval-evaluations/default "$retrieval_file" "${auth_header[@]}"
grep -q '"totalCases"' "$retrieval_file"

prompt_injection_file="$tmp_dir/prompt-injection.json"
log "prompt injection evaluation"
request GET /v1/prompt-injection-evaluations/default "$prompt_injection_file" "${auth_header[@]}"
grep -q '"totalCases"' "$prompt_injection_file"

metrics_file="$tmp_dir/metrics.txt"
log "metrics"
request GET /metrics "$metrics_file"
grep -q 'knowledge_retrieval_vector_accepted_total' "$metrics_file"
grep -q 'knowledge_reindex_jobs_accepted_total' "$metrics_file"

if [[ "$RUN_ADVICE_SMOKE" == "true" ]]; then
  advice_file="$tmp_dir/advice.json"
  log "advice"
  request POST /v1/management/advice "$advice_file" \
    "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d '{
      "memberContext": {
        "situation": "週報の提出が遅れているメンバーに1on1で改善を促したい",
        "targetGoal": "心理的安全性を保ちながら次の行動を合意したい"
      },
      "setting": {
        "tone": "empathetic",
        "model": "gpt-4o-mini"
      }
    }'
  grep -q '"advice"' "$advice_file"
fi

log "ok"
