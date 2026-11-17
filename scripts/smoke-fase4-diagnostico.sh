#!/usr/bin/env bash
set -euo pipefail
: "${EXECUTION_SERVICE_TOKEN:?Configure o token local}"
: "${BOOTSTRAP_ADMIN_USERNAME:?Configure o usuário local}"
: "${BOOTSTRAP_ADMIN_PASSWORD:?Configure a senha local}"
compose=(docker compose -p oficina-fase4-e2e -f compose.fase4.yaml)
branch=00000000-0000-4000-8000-000000000001
api=http://127.0.0.1:18080
execution=http://127.0.0.1:18082
for attempt in {1..60}; do
  if curl -fsS "$api/actuator/health/readiness" >/dev/null 2>&1; then break; fi
  [[ $attempt == 60 ]] && exit 1
  sleep 1
done
login=$(jq -nc --arg username "$BOOTSTRAP_ADMIN_USERNAME" --arg password "$BOOTSTRAP_ADMIN_PASSWORD" '{username:$username,password:$password}')
token=$(curl -fsS -H 'Content-Type: application/json' -d "$login" "$api/api/auth/login")
"${compose[@]}" stop execution >/dev/null
body='{"cliente":{"nome":"Cliente Teste","cpfCnpj":"12345678909","email":"teste@example.org","telefone":"11999999999"},"veiculo":{"placa":"ABC1234","marca":"Teste","modelo":"Teste","ano":2020}}'
response=$(curl -fsS -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body" "$api/api/ordens-servico")
order=$(jq -er '.id' <<< "$response")
[[ $order =~ ^[0-9a-fA-F-]{36}$ ]]
pending=$("${compose[@]}" exec -T os-db psql -U oficina -d oficina -tAc "select count(*) from execution_outbox where order_id='$order' and delivered_at is null")
[[ $pending == 1 ]] || { echo 'Solicitação não ficou pendente' >&2; exit 1; }
"${compose[@]}" start execution >/dev/null
for attempt in {1..60}; do
  if curl -fsS -H "X-Service-Token: $EXECUTION_SERVICE_TOKEN" "$execution/api/branches/$branch/work-orders/$order" >/dev/null 2>&1; then break; fi
  [[ $attempt == 60 ]] && { echo 'OS não chegou à Execução' >&2; exit 1; }
  sleep 1
done
curl -fsS -o /dev/null -X POST -H "X-Service-Token: $EXECUTION_SERVICE_TOKEN" "$execution/api/branches/$branch/work-orders/$order/diagnosis/start"
curl -fsS -o /dev/null -X POST -H "X-Service-Token: $EXECUTION_SERVICE_TOKEN" -H 'Content-Type: application/json' -d '{"description":"Diagnostico sintetico"}' "$execution/api/branches/$branch/work-orders/$order/diagnosis/complete"
for attempt in {1..60}; do
  response=$(curl -fsS -H "Authorization: Bearer $token" "$api/api/ordens-servico/$order")
  if jq -e '.status == "DIAGNOSTICO" and .diagnostico == "Diagnostico sintetico"' >/dev/null <<< "$response"; then
    echo 'Abertura real, recuperação após indisponibilidade e retorno de diagnóstico passaram.'
    exit 0
  fi
  sleep 1
done
echo 'Diagnóstico não chegou à OS' >&2
exit 1
