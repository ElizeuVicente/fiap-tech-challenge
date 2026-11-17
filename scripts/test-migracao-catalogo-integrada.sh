#!/usr/bin/env bash
set -euo pipefail

# Teste local descartável; nomes exclusivos, sem volumes e sem conexão com AWS.
root_dir=$(cd "$(dirname "$0")/.." && pwd)
run_id=$(tr -d '-' < /proc/sys/kernel/random/uuid)
network="catalog-test-$run_id"
pg="catalog-pg-$run_id"
mongo="catalog-mongo-$run_id"
execution="catalog-execution-$run_id"
image="catalog-execution-test:$run_id"
cleanup() {
  docker rm -f "$execution" "$mongo" "$pg" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  docker image rm "$image" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker network create "$network" >/dev/null
docker run -d --name "$pg" --network "$network" -e POSTGRES_PASSWORD=local-test -e POSTGRES_DB=oficina postgres:15-alpine >/dev/null
docker run -d --name "$mongo" --network "$network" mongo:7 >/dev/null
docker build -q -t "$image" "$root_dir/../oficina-execution-service" >/dev/null
export CATALOG_EXECUTION_TOKEN='local-test-token-01234567890123456789'
docker run -d --name "$execution" --network "$network" -p 127.0.0.1::8082 \
  -e EXECUTION_MONGO_URI="mongodb://$mongo:27017/execution" \
  -e EXECUTION_SERVICE_TOKEN="$CATALOG_EXECUTION_TOKEN" "$image" >/dev/null
for attempt in {1..60}; do
  docker exec "$pg" pg_isready -U postgres -d oficina >/dev/null 2>&1 && break
  [[ "$attempt" == 60 ]] && { echo 'PostgreSQL não ficou pronto' >&2; exit 1; }
  sleep 1
done
port=$(docker port "$execution" 8082/tcp | awk -F: 'NR==1 {print $NF}')
export CATALOG_EXECUTION_URL="http://127.0.0.1:$port"
for attempt in {1..90}; do
  curl -fsS "$CATALOG_EXECUTION_URL/actuator/health" >/dev/null 2>&1 && break
  [[ "$attempt" == 90 ]] && { echo 'Execução não ficou pronta' >&2; exit 1; }
  sleep 1
done
docker exec "$pg" psql -U postgres -d oficina -v ON_ERROR_STOP=1 -c "CREATE TABLE peca (id uuid primary key, nome text, preco numeric, quantidade_estoque integer); CREATE TABLE servico (id uuid primary key, nome text, preco_base numeric); INSERT INTO peca VALUES ('22222222-2222-2222-2222-222222222222','Filtro',15.50,5); INSERT INTO servico VALUES ('33333333-3333-3333-3333-333333333333','Reparo',50.00);" >/dev/null
export CATALOG_DATABASE_URL='test-container'
export CATALOG_BRANCH_ID='11111111-1111-1111-1111-111111111111'
export CATALOG_CONFIRM_IMPORT=YES
export CATALOG_TEST_POSTGRES="$pg"
psql() { shift; docker exec "$CATALOG_TEST_POSTGRES" psql -U postgres -d oficina "$@"; }
export -f psql
bash "$root_dir/scripts/migrar-catalogo-fase4.sh" --dry-run
bash "$root_dir/scripts/migrar-catalogo-fase4.sh" --apply
bash "$root_dir/scripts/migrar-catalogo-fase4.sh" --apply
bash "$root_dir/scripts/migrar-catalogo-fase4.sh" --check
part=$(curl -fsS -H "X-Service-Token: $CATALOG_EXECUTION_TOKEN" "$CATALOG_EXECUTION_URL/api/branches/$CATALOG_BRANCH_ID/parts/22222222-2222-2222-2222-222222222222/catalog")
service=$(curl -fsS -H "X-Service-Token: $CATALOG_EXECUTION_TOKEN" "$CATALOG_EXECUTION_URL/api/branches/$CATALOG_BRANCH_ID/services/33333333-3333-3333-3333-333333333333/catalog")
jq -e '.description=="Filtro" and .unitPrice==15.50' >/dev/null <<< "$part"
jq -e '.description=="Reparo" and .unitPrice==50.00' >/dev/null <<< "$service"
docker exec "$pg" psql -U postgres -d oficina -v ON_ERROR_STOP=1 -c 'UPDATE peca SET preco=19.00' >/dev/null
if bash "$root_dir/scripts/migrar-catalogo-fase4.sh" --check >/dev/null 2>&1; then
  echo 'A conciliação deveria identificar preço divergente' >&2; exit 1
fi
curl -fsS -o /dev/null -X POST -H "X-Service-Token: $CATALOG_EXECUTION_TOKEN" -H 'Content-Type: application/json' \
  -d '{"available":5}' "$CATALOG_EXECUTION_URL/api/branches/$CATALOG_BRANCH_ID/parts/22222222-2222-2222-2222-222222222222/stock"
bash "$root_dir/scripts/conciliar-estoque-fase4.sh"
docker exec "$pg" psql -U postgres -d oficina -v ON_ERROR_STOP=1 -c 'UPDATE peca SET quantidade_estoque=6' >/dev/null
if bash "$root_dir/scripts/conciliar-estoque-fase4.sh" >/dev/null 2>&1; then
  echo 'A conciliação deveria identificar saldo divergente' >&2; exit 1
fi
echo 'Migração integrada e repetição idempotente: OK'
