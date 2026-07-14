#!/usr/bin/env bash

set -Eeuo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
WEBHOOK_SECRET="${WEBHOOK_SECRET:-dev-webhook-secret-change-me}"
RUN_ID="${RUN_ID:-$(date +%s)}"
DEMO_USERNAME="${DEMO_USERNAME:-demo-${RUN_ID}}"
PASSWORD="${PASSWORD:-senha123456}"
CPF_CNPJ="${CPF_CNPJ:-98765432100}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Comando obrigatorio nao encontrado: $1" >&2
    exit 1
  }
}

request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local response
  local status
  local content

  if [[ -n "$body" ]]; then
    response="$(curl -sS -X "$method" "$url" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${TOKEN:-}" \
      -d "$body" \
      -w $'\n%{http_code}')"
  else
    response="$(curl -sS -X "$method" "$url" \
      -H "Authorization: Bearer ${TOKEN:-}" \
      -w $'\n%{http_code}')"
  fi

  status="${response##*$'\n'}"
  content="${response%$'\n'*}"

  if (( status >= 400 )); then
    printf '%s\n' "$content" >&2
    return 1
  fi

  printf '%s' "$content"
}

extract_id() {
  grep -oE '"id":"[^"]+"' | head -n 1 | cut -d '"' -f 4
}

require_command curl

echo "== Oficina Tech Challenge | Consumo da API =="
echo "BASE_URL=${BASE_URL}"
echo

echo "1/9 - Healthcheck"
curl -fsS "${BASE_URL}/actuator/health"
echo
echo

echo "2/9 - Registro de usuario"
curl -fsS -X POST "${BASE_URL}/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${DEMO_USERNAME}\",\"password\":\"${PASSWORD}\"}" >/dev/null 2>&1 || true
echo "OK"
echo

echo "3/9 - Login"
TOKEN="$(curl -fsS -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${DEMO_USERNAME}\",\"password\":\"${PASSWORD}\"}")"
echo "Token recebido: ${TOKEN:0:24}..."
echo

echo "4/9 - Cadastro de servico"
SERVICO_RESPONSE="$(request POST "${BASE_URL}/api/servicos" "{
  \"nome\": \"Troca de oleo demo ${RUN_ID}\",
  \"precoBase\": 180.00,
  \"tempoEstimado\": 60
}")"
SERVICO_ID="$(printf '%s' "$SERVICO_RESPONSE" | extract_id)"
echo "$SERVICO_RESPONSE"
echo

echo "5/9 - Cadastro de peca"
PECA_RESPONSE="$(request POST "${BASE_URL}/api/pecas" "{
  \"nome\": \"Filtro de oleo demo ${RUN_ID}\",
  \"preco\": 45.00,
  \"quantidadeEstoque\": 100
}")"
PECA_ID="$(printf '%s' "$PECA_RESPONSE" | extract_id)"
echo "$PECA_RESPONSE"
echo

echo "6/9 - Abertura completa de OS"
PLACA="$(printf 'DEM%dA%02d' "$((RUN_ID % 10))" "$((RUN_ID % 100))")"
OS_RESPONSE="$(request POST "${BASE_URL}/api/ordens-servico" "{
  \"cliente\": {
    \"nome\": \"Cliente Demo ${RUN_ID}\",
    \"cpfCnpj\": \"${CPF_CNPJ}\",
    \"email\": \"cliente.demo.${RUN_ID}@example.com\",
    \"telefone\": \"11999999999\"
  },
  \"veiculo\": {
    \"placa\": \"${PLACA}\",
    \"marca\": \"Toyota\",
    \"modelo\": \"Corolla\",
    \"ano\": 2022
  },
  \"servicos\": [{ \"servicoId\": \"${SERVICO_ID}\" }],
  \"pecas\": [{ \"pecaId\": \"${PECA_ID}\", \"quantidade\": 1 }]
}")"
OS_ID="$(printf '%s' "$OS_RESPONSE" | extract_id)"
echo "$OS_RESPONSE"
echo

echo "7/9 - Registrar diagnostico e gerar orcamento"
request PATCH "${BASE_URL}/api/ordens-servico/${OS_ID}/diagnostico" "{
  \"diagnostico\": \"Diagnostico demo ${RUN_ID}\"
}"
echo
request PATCH "${BASE_URL}/api/ordens-servico/${OS_ID}/orcamento"
echo
echo

echo "8/9 - Aprovacao via webhook externo"
curl -fsS -X POST "${BASE_URL}/api/ordens-servico/${OS_ID}/orcamento/notificacoes" \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Secret: ${WEBHOOK_SECRET}" \
  -d "{
    \"decisao\": \"APROVADO\",
    \"origem\": \"demo-gravacao\",
    \"dataHora\": \"2026-07-10T10:00:00\",
    \"identificadorExterno\": \"demo-${RUN_ID}\"
  }"
echo
echo

echo "9/9 - Consulta de status, listagem operacional e monitoramento"
request GET "${BASE_URL}/api/ordens-servico/${OS_ID}/status"
echo
request GET "${BASE_URL}/api/ordens-servico?operacional=true"
echo
request GET "${BASE_URL}/api/ordens-servico/monitoramento"
echo
echo

echo "Demo concluida. OS_ID=${OS_ID}"
