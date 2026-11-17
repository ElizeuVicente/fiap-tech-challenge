#!/usr/bin/env bash
set -euo pipefail
: "${EXECUTION_SERVICE_TOKEN:?Configure o token local de Execução}"
: "${BILLING_SERVICE_TOKEN:?Configure o token local do Billing}"
: "${BOOTSTRAP_ADMIN_USERNAME:?Configure o usuário local}"
: "${BOOTSTRAP_ADMIN_PASSWORD:?Configure a senha local}"
compose=(docker compose -p oficina-fase4-e2e -f compose.fase4.yaml)
api=http://127.0.0.1:18080
login=$(jq -nc --arg username "$BOOTSTRAP_ADMIN_USERNAME" --arg password "$BOOTSTRAP_ADMIN_PASSWORD" '{username:$username,password:$password}')
for attempt in {1..60}; do
  if token=$(curl -fsS -H 'Content-Type: application/json' -d "$login" "$api/api/auth/login" 2>/dev/null); then break; fi
  [[ $attempt == 60 ]] && { echo 'OS indisponível' >&2; exit 1; }
  sleep 1
done
service=$(curl -fsS -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{"nome":"Servico sintetico","precoBase":42.50,"tempoEstimado":30}' "$api/api/servicos" | jq -er '.id')
body=$(jq -nc --arg service "$service" '{cliente:{nome:"Cliente Teste",cpfCnpj:"12345678909",email:"teste@example.org",telefone:"11999999999"},veiculo:{placa:"ABC1234",marca:"Teste",modelo:"Teste",ano:2020},servicos:[{servicoId:$service}]}')
order=$(curl -fsS -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body" "$api/api/ordens-servico" | jq -er '.id')
[[ $order =~ ^[0-9a-fA-F-]{36}$ ]]
curl -fsS -o /dev/null -X PATCH -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{"diagnostico":"Diagnostico sintetico"}' "$api/api/ordens-servico/$order/diagnostico"
curl -fsS -o /dev/null -X PATCH -H "Authorization: Bearer $token" "$api/api/ordens-servico/$order/orcamento"
for attempt in {1..60}; do
  quote=$("${compose[@]}" exec -T billing-db psql -U billing -d billing -tAc "select id from quotes where order_id='$order'")
  saga=$("${compose[@]}" exec -T os-db psql -U oficina -d oficina -tAc "select count(*) from billing_saga where order_id='$order' and status='AGUARDANDO_DECISAO'")
  if [[ $quote =~ ^[0-9a-fA-F-]{36}$ && $saga == 1 ]]; then break; fi
  [[ $attempt == 60 ]] && { echo 'Orçamento/Saga não chegou' >&2; exit 1; }
  sleep 1
done
curl -fsS -o /dev/null -H "X-Service-Token: $BILLING_SERVICE_TOKEN" -H 'Content-Type: application/json' -d '{"status":"APPROVED"}' "http://127.0.0.1:18081/api/quotes/$quote/decision"
for attempt in {1..60}; do
  response=$(curl -fsS -H "Authorization: Bearer $token" "$api/api/ordens-servico/$order")
  if jq -e '.status == "AGUARDANDO_PAGAMENTO"' >/dev/null <<< "$response"; then
    echo 'OS → Billing → Saga: orçamento aprovado, OS aguardando pagamento.'
    exit 0
  fi
  sleep 1
done
echo 'OS não entrou em AGUARDANDO_PAGAMENTO' >&2
exit 1
