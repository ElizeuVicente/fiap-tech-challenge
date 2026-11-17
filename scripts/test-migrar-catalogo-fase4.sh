#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -r "$test_dir"' EXIT
export CATALOG_DATABASE_URL='synthetic-local-only'
export CATALOG_BRANCH_ID='11111111-1111-1111-1111-111111111111'
export CATALOG_EXECUTION_URL='http://example.invalid'
export CATALOG_EXECUTION_TOKEN='synthetic-token'
export CATALOG_CONFIRM_IMPORT=YES
export CATALOG_TEST_DIR="$test_dir"

psql() { printf '%s\n' '{"type":"parts","id":"22222222-2222-2222-2222-222222222222","description":"Filtro","unitPrice":15.50}' '{"type":"services","id":"33333333-3333-3333-3333-333333333333","description":"Reparo","unitPrice":50.00}'; }
curl() { printf '%s\n' "$*" >> "$CATALOG_TEST_DIR/calls"; printf '%s' "${CATALOG_TEST_HTTP_STATUS:-200}"; }
export -f psql curl

output=$(bash "$root_dir/scripts/migrar-catalogo-fase4.sh" --dry-run)
[[ "$output" == *'itens: 2; inválidos: 0; falhas: 0'* ]]
[[ ! -e "$test_dir/calls" ]]

output=$(bash "$root_dir/scripts/migrar-catalogo-fase4.sh" --apply)
[[ "$output" == *'itens: 2; inválidos: 0; falhas: 0'* ]]
[[ $(wc -l < "$test_dir/calls") -eq 2 ]]
grep -q '/parts/22222222-2222-2222-2222-222222222222/catalog/import' "$test_dir/calls"
grep -q '/services/33333333-3333-3333-3333-333333333333/catalog/import' "$test_dir/calls"

export CATALOG_TEST_HTTP_STATUS=409
if bash "$root_dir/scripts/migrar-catalogo-fase4.sh" --apply > /dev/null 2>&1; then
  echo 'Conflitos HTTP devem produzir falha' >&2; exit 1
fi
echo 'Prévia, importação e conflitos: OK'

psql() { printf '%s\n' '{"type":"parts","id":"22222222-2222-2222-2222-222222222222","description":"Filtro","unitPrice":15.50}' '{"type":"services","id":"33333333-3333-3333-3333-333333333333","description":"Inválido","unitPrice":0}'; }
export -f psql
before=$(wc -l < "$test_dir/calls")
if bash "$root_dir/scripts/migrar-catalogo-fase4.sh" --apply > /dev/null 2>&1; then
  echo 'Dados inválidos deveriam impedir a importação' >&2; exit 1
fi
[[ $(wc -l < "$test_dir/calls") -eq "$before" ]]
echo 'Dados inválidos bloqueiam todos os envios: OK'
