#!/usr/bin/env bash
set -euo pipefail

# Leitura apenas. A comparação exige que operações de estoque estejam pausadas.
: "${CATALOG_DATABASE_URL:?Defina a URL PostgreSQL da OS fora do Git}"
: "${CATALOG_BRANCH_ID:?Defina a filial de destino}"
: "${CATALOG_EXECUTION_URL:?Defina a URL da Execução}"
: "${CATALOG_EXECUTION_TOKEN:?Defina o token interno fora do Git}"
[[ "$CATALOG_BRANCH_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] || { echo 'Filial deve ser UUID válido' >&2; exit 2; }
for binary in psql jq curl; do command -v "$binary" >/dev/null || { echo "Comando ausente: $binary" >&2; exit 2; }; done

data=$(psql "$CATALOG_DATABASE_URL" -X -v ON_ERROR_STOP=1 -A -t -c "SELECT json_build_object('id',id,'available',quantidade_estoque)::text FROM peca")
total=0; divergences=0
while IFS= read -r row; do
  [[ -z "$row" ]] && continue
  total=$((total+1))
  id=$(jq -er '.id | select(type=="string")' <<< "$row")
  legacy=$(jq -er '.available | select(type=="number" and .>=0 and floor==.)' <<< "$row")
  response=$(curl -fsS --max-time 10 -H "X-Service-Token: $CATALOG_EXECUTION_TOKEN" \
    "${CATALOG_EXECUTION_URL%/}/api/branches/$CATALOG_BRANCH_ID/parts/$id/stock") || {
      echo "Estoque indisponível ou não cadastrado: peça=$id" >&2; divergences=$((divergences+1)); continue;
    }
  if ! jq -e --arg branch "$CATALOG_BRANCH_ID" --arg part "$id" --argjson legacy "$legacy" \
      '.branchId==$branch and .partId==$part and .available==$legacy' >/dev/null <<< "$response"; then
    echo "Estoque divergente ou resposta inválida: peça=$id" >&2
    divergences=$((divergences+1))
  fi
done <<< "$data"
printf 'Peças verificadas: %s; divergências: %s\n' "$total" "$divergences"
echo 'A comparação é informativa: pause escritores e verifique reservas/consumos antes do corte.'
[[ "$divergences" -eq 0 ]]
