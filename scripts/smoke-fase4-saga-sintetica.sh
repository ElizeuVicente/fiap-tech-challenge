#!/usr/bin/env bash
set -euo pipefail
# Isolated local test only: inserts a synthetic payment event, not a Mercado Pago payment.
: "${EXECUTION_SERVICE_TOKEN:?Configure o token local de Execução}"
: "${BILLING_SERVICE_TOKEN:?Configure o token local do Billing}"
: "${BOOTSTRAP_ADMIN_USERNAME:?Configure o usuário local}"
: "${BOOTSTRAP_ADMIN_PASSWORD:?Configure a senha local}"
[[ ${DEMO_ALLOW_SYNTHETIC_PAYMENT:-} == 1 ]] || { echo 'Teste exige DEMO_ALLOW_SYNTHETIC_PAYMENT=1' >&2; exit 1; }
compose=(docker compose -p fiap-saga-demo-5921 -f compose.fase4.yaml)
api=http://127.0.0.1:18080
execution=http://127.0.0.1:18082
branch=00000000-0000-4000-8000-000000000001
login=$(jq -nc --arg username "$BOOTSTRAP_ADMIN_USERNAME" --arg password "$BOOTSTRAP_ADMIN_PASSWORD" '{username:$username,password:$password}')
token=$(curl -fsS -H 'Content-Type: application/json' -d "$login" "$api/api/auth/login")
service=$(curl -fsS -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{"nome":"Servico sintetico","precoBase":42.50,"tempoEstimado":30}' "$api/api/servicos" | jq -er '.id')
part=$(curl -fsS -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{"nome":"Peca sintetica","preco":10.00,"quantidadeEstoque":5}' "$api/api/pecas" | jq -er '.id')
if [[ ${OFICINA_EXECUTION_CATALOG_ENABLED:-false} == true ]]; then
  # Prices intentionally differ from the legacy catalog to prove which source is used.
  curl -fsS -o /dev/null -X PUT -H "X-Service-Token: $EXECUTION_SERVICE_TOKEN" -H 'Content-Type: application/json' -d '{"description":"Servico externo","unitPrice":48.50}' "$execution/api/branches/$branch/services/$service/catalog/import"
  curl -fsS -o /dev/null -X PUT -H "X-Service-Token: $EXECUTION_SERVICE_TOKEN" -H 'Content-Type: application/json' -d '{"description":"Peca externa","unitPrice":11.25}' "$execution/api/branches/$branch/parts/$part/catalog/import"
fi
curl -fsS -o /dev/null -H "X-Service-Token: $EXECUTION_SERVICE_TOKEN" -H 'Content-Type: application/json' -d '{"available":5}' "$execution/api/branches/$branch/parts/$part/stock"
body=$(jq -nc --arg service "$service" --arg part "$part" '{cliente:{nome:"Cliente Teste",cpfCnpj:"12345678909",email:"teste@example.org",telefone:"11999999999"},veiculo:{placa:"ABC1234",marca:"Teste",modelo:"Teste",ano:2020},servicos:[{servicoId:$service}],pecas:[{pecaId:$part,quantidade:2}]}')
order=$(curl -fsS -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body" "$api/api/ordens-servico" | jq -er '.id')
[[ $order =~ ^[0-9a-fA-F-]{36}$ ]]
for attempt in {1..60}; do
  if curl -fsS -H "X-Service-Token: $EXECUTION_SERVICE_TOKEN" "$execution/api/branches/$branch/work-orders/$order" >/dev/null 2>&1; then break; fi
  [[ $attempt == 60 ]] && { echo 'Ordem não chegou à Execução' >&2; exit 1; }; sleep 1
done
curl -fsS -o /dev/null -X POST -H "X-Service-Token: $EXECUTION_SERVICE_TOKEN" "$execution/api/branches/$branch/work-orders/$order/diagnosis/start"
curl -fsS -o /dev/null -X POST -H "X-Service-Token: $EXECUTION_SERVICE_TOKEN" -H 'Content-Type: application/json' -d '{"description":"Diagnostico sintetico"}' "$execution/api/branches/$branch/work-orders/$order/diagnosis/complete"
for attempt in {1..60}; do
  status=$(curl -fsS -H "Authorization: Bearer $token" "$api/api/ordens-servico/$order" | jq -r '.status')
  [[ $status == DIAGNOSTICO ]] && break
  [[ $attempt == 60 ]] && { echo 'Diagnóstico não chegou' >&2; exit 1; }; sleep 1
done
curl -fsS -o /dev/null -X PATCH -H "Authorization: Bearer $token" "$api/api/ordens-servico/$order/orcamento"
for attempt in {1..60}; do
  quote=$("${compose[@]}" exec -T billing-db psql -U billing -d billing -tAc "select id from quotes where order_id='$order'")
  state=$("${compose[@]}" exec -T os-db psql -U oficina -d oficina -tAc "select status from billing_saga where order_id='$order'")
  [[ $quote =~ ^[0-9a-fA-F-]{36}$ && $state == AGUARDANDO_DECISAO ]] && break
  [[ $attempt == 60 ]] && { echo 'Orçamento/Saga não chegou' >&2; exit 1; }; sleep 1
done
if [[ ${OFICINA_EXECUTION_CATALOG_ENABLED:-false} == true ]]; then
  snapshot=$("${compose[@]}" exec -T os-db psql -U oficina -d oficina -tAc "select (select count(*) from item_peca where ordem_servico_id='$order' and peca_id is null and referencia_externa_id='$part' and preco_aplicado=11.25) + (select count(*) from item_servico where ordem_servico_id='$order' and servico_id is null and referencia_externa_id='$service' and preco_aplicado=48.50)")
  total=$("${compose[@]}" exec -T billing-db psql -U billing -d billing -tAc "select amount from quotes where id='$quote'")
  [[ "$snapshot" == 2 && "$total" == 71.00 ]] || { echo "Snapshot ou valor externo incorreto: itens=$snapshot total=$total" >&2; exit 1; }
fi
curl -fsS -o /dev/null -H "X-Service-Token: $BILLING_SERVICE_TOKEN" -H 'Content-Type: application/json' -d '{"status":"APPROVED"}' "http://127.0.0.1:18081/api/quotes/$quote/decision"
for attempt in {1..60}; do
  state=$("${compose[@]}" exec -T os-db psql -U oficina -d oficina -tAc "select status from billing_saga where order_id='$order'")
  [[ $state == APROVADA ]] && break
  [[ $attempt == 60 ]] && { echo 'Aprovação não chegou' >&2; exit 1; }; sleep 1
done
event_id=$(cat /proc/sys/kernel/random/uuid)
payment_attempt=$(cat /proc/sys/kernel/random/uuid)
"${compose[@]}" exec -T billing-db psql -v ON_ERROR_STOP=1 -U billing -d billing -c "INSERT INTO outbox_events(event_id,aggregate_id,event_type,payload,occurred_at) SELECT '$event_id',id,'PagamentoConfirmado',json_build_object('eventId','$event_id','eventType','PagamentoConfirmado','schemaVersion',1,'occurredAt',to_char(now() at time zone 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"'),'sagaId',saga_id,'osId',order_id,'filialId',branch_id,'correlationId',correlation_id,'payload',json_build_object('quoteId',id,'attemptId','$payment_attempt','providerPaymentId','123456','amount',amount,'status','APPROVED'))::text,now() FROM quotes WHERE id='$quote'" >/dev/null
for attempt in {1..100}; do
  state=$("${compose[@]}" exec -T os-db psql -U oficina -d oficina -tAc "select status from billing_saga where order_id='$order'")
  [[ $state == EM_EXECUCAO ]] && break
  [[ $attempt == 100 ]] && { echo "Saga não iniciou execução: $state" >&2; exit 1; }; sleep 1
done
available=$(curl -fsS -H "X-Service-Token: $EXECUTION_SERVICE_TOKEN" "$execution/api/branches/$branch/parts/$part/stock" | jq -er '.available')
[[ $available == 3 ]] || { echo "Estoque inesperado após consumo: $available" >&2; exit 1; }
curl -fsS -o /dev/null -X POST -H "X-Service-Token: $EXECUTION_SERVICE_TOKEN" "$execution/api/branches/$branch/work-orders/$order/complete"
for attempt in {1..60}; do
  status=$(curl -fsS -H "Authorization: Bearer $token" "$api/api/ordens-servico/$order" | jq -r '.status')
  [[ $status == FINALIZADA ]] && { echo 'Jornada local finalizada com evento de pagamento SIMULADO.'; exit 0; }
  [[ $attempt == 60 ]] && { echo "OS não foi finalizada: $status" >&2; exit 1; }; sleep 1
done
