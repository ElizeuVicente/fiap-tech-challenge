#!/usr/bin/env bash
set -euo pipefail

# Exercises local containers only; leaves one synthetic quote in the named volumes.
base_url="${BILLING_URL:-http://127.0.0.1:18081}"
: "${BILLING_SERVICE_TOKEN:?Configure o token local do Billing}"
for attempt in {1..30}; do
  if curl -fsS "$base_url/actuator/health" >/dev/null; then break; fi
  [[ "$attempt" == 30 ]] && { echo 'Billing health check timed out' >&2; exit 1; }
  sleep 1
done
order_id="$(uuidgen)"
branch_id="$(uuidgen)"
body="$(jq -nc --arg branch "$branch_id" --arg order "$order_id" '{branchId:$branch,orderId:$order,amount:42.50}')"
response="$(curl -fsS -H "X-Service-Token: $BILLING_SERVICE_TOKEN" -H 'Content-Type: application/json' -d "$body" "$base_url/api/quotes")"
quote_id="$(jq -er '.id' <<< "$response")"
[[ "$quote_id" =~ ^[0-9a-fA-F-]{36}$ && "$order_id" =~ ^[0-9a-fA-F-]{36}$ ]] || { echo 'Invalid UUID' >&2; exit 1; }
for attempt in 1 2; do
  curl -fsS -o /dev/null -H "X-Service-Token: $BILLING_SERVICE_TOKEN" -H 'Content-Type: application/json' -d '{"status":"APPROVED"}' "$base_url/api/quotes/$quote_id/decision"
done
for attempt in {1..30}; do
  billing_count="$(docker compose -f compose.fase4.yaml exec -T billing-db psql -U billing -d billing -tAc "select count(*) from outbox_events where aggregate_id='$quote_id' and event_type in ('OrcamentoGerado','OrcamentoAprovado') and published_at is not null")"
  os_count="$(docker compose -f compose.fase4.yaml exec -T os-db psql -U oficina -d oficina -tAc "select count(*) from billing_inbox where order_id='$order_id'")"
  quote_status="$(docker compose -f compose.fase4.yaml exec -T billing-db psql -U billing -d billing -tAc "select status from quotes where id='$quote_id'")"
  saga_status="$(docker compose -f compose.fase4.yaml exec -T os-db psql -U oficina -d oficina -tAc "select status from billing_saga where order_id='$order_id'")"
  command_published="$(docker compose -f compose.fase4.yaml exec -T os-db psql -U oficina -d oficina -tAc "select count(*) from billing_command_outbox where saga_id=(select saga_id from billing_saga where order_id='$order_id') and published_at is not null")"
  if [[ "$billing_count" == 2 && "$os_count" == 2 && "$quote_status" == CANCELLED && "$saga_status" == COMPENSACAO_PENDENTE && "$command_published" == 1 ]]; then
    billing_ids="$(docker compose -f compose.fase4.yaml exec -T billing-db psql -U billing -d billing -tAc "select string_agg(event_id::text, ',' order by event_id::text) from outbox_events where aggregate_id='$quote_id' and event_type in ('OrcamentoGerado','OrcamentoAprovado')")"
    os_ids="$(docker compose -f compose.fase4.yaml exec -T os-db psql -U oficina -d oficina -tAc "select string_agg(event_id::text, ',' order by event_id::text) from billing_inbox where order_id='$order_id'")"
    [[ "$billing_ids" == "$os_ids" ]] || { echo 'Event IDs differ' >&2; exit 1; }
    echo 'Billing → RabbitMQ → OS: decisão foi entregue uma vez; falha simulada da OS gerou e publicou compensação, e o Billing cancelou o orçamento.'
    exit 0
  fi
  sleep 1
done
echo "Timed out: published=$billing_count received=$os_count quote=$quote_status saga=$saga_status compensation=$command_published" >&2
exit 1
