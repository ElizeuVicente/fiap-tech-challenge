#!/usr/bin/env bash
set -euo pipefail

# A prévia lê somente IDs e quantidades; --apply precisa de confirmação explícita.
: "${CATALOG_DATABASE_URL:?Defina a URL PostgreSQL da OS fora do Git}"
: "${CATALOG_BRANCH_ID:?Defina a filial de destino}"

mode=${1:---dry-run}
if [[ "$mode" != '--dry-run' && "$mode" != '--check' && "$mode" != '--apply' ]]; then
  echo 'Uso: migrar-catalogo-fase4.sh [--dry-run|--check|--apply]' >&2; exit 2
fi
if [[ ! "$CATALOG_BRANCH_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
  echo 'Filial deve ser UUID válido' >&2; exit 2
fi
for binary in psql jq; do command -v "$binary" >/dev/null || { echo "Comando ausente: $binary" >&2; exit 2; }; done

if [[ "$mode" != '--dry-run' ]]; then
  command -v curl >/dev/null || { echo 'Comando ausente: curl' >&2; exit 2; }
  : "${CATALOG_EXECUTION_URL:?Defina a URL da Execução}"
  : "${CATALOG_EXECUTION_TOKEN:?Defina o token interno fora do Git}"
  if [[ "$mode" == '--apply' ]]; then
    : "${CATALOG_CONFIRM_IMPORT:?Defina CATALOG_CONFIRM_IMPORT=YES após revisar a prévia}"
    [[ "$CATALOG_CONFIRM_IMPORT" == YES ]] || { echo 'Importação não confirmada' >&2; exit 2; }
  fi
fi

query="SELECT json_build_object('type','parts','id',id,'description',nome,'unitPrice',preco)::text FROM peca UNION ALL SELECT json_build_object('type','services','id',id,'description',nome,'unitPrice',preco_base)::text FROM servico"
data=$(psql "$CATALOG_DATABASE_URL" -X -v ON_ERROR_STOP=1 -A -t -c "$query")
total=0; invalid=0; failed=0
# Validate the entire source snapshot before any destination mutation.
while IFS= read -r row; do
  [[ -z "$row" ]] && continue
  total=$((total+1))
  if ! jq -e '(.type=="parts" or .type=="services") and (.id|type=="string" and test("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) and (.description|type=="string" and length<=200 and test("[^[:space:]]")) and (.unitPrice|type=="number" and . > 0 and . <= 9999999999999.99)' >/dev/null <<< "$row"; then
    invalid=$((invalid+1))
  fi
done <<< "$data"
if [[ "$invalid" -gt 0 ]]; then
  printf 'Modo: %s; itens: %s; inválidos: %s; falhas: 0\n' "$mode" "$total" "$invalid"
  echo 'Corrija os dados inválidos antes de importar; nenhum item foi enviado.' >&2
  exit 1
fi
if [[ "$mode" == '--dry-run' ]]; then
  printf 'Modo: %s; itens: %s; inválidos: 0; falhas: 0\n' "$mode" "$total"
  exit 0
fi
while IFS= read -r row; do
  [[ -z "$row" ]] && continue
  kind=$(jq -r '.type' <<< "$row"); id=$(jq -r '.id' <<< "$row")
  payload=$(jq -c '{description,unitPrice}' <<< "$row")
  if [[ "$mode" == '--check' ]]; then
    response=$(curl -fsS --max-time 10 -H "X-Service-Token: $CATALOG_EXECUTION_TOKEN" \
      "${CATALOG_EXECUTION_URL%/}/api/branches/$CATALOG_BRANCH_ID/$kind/$id/catalog") || {
        echo "Ausente ou indisponível: tipo=$kind id=$id" >&2; failed=$((failed+1)); continue;
      }
    if ! jq -e --argjson expected "$payload" --arg branch "$CATALOG_BRANCH_ID" --arg id "$id" --arg kind "$kind" \
      '.branchId==$branch and (if $kind=="parts" then .partId else .serviceId end)==$id and .description==($expected.description|gsub("^\\s+|\\s+$";"")) and .unitPrice==$expected.unitPrice' >/dev/null <<< "$response"; then
      echo "Divergente: tipo=$kind id=$id" >&2; failed=$((failed+1))
    fi
    continue
  fi
  status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 -X PUT \
    -H "X-Service-Token: $CATALOG_EXECUTION_TOKEN" -H 'Content-Type: application/json' \
    --data "$payload" "${CATALOG_EXECUTION_URL%/}/api/branches/$CATALOG_BRANCH_ID/$kind/$id/catalog/import") || status=000
  if [[ "$status" != 2* ]]; then
    echo "Falha: tipo=$kind id=$id HTTP=$status" >&2; failed=$((failed+1))
  fi
done <<< "$data"
printf 'Modo: %s; itens: %s; inválidos: %s; falhas: %s\n' "$mode" "$total" "$invalid" "$failed"
[[ "$invalid" -eq 0 && "$failed" -eq 0 ]]
