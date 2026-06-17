#!/usr/bin/env bash

set -Eeuo pipefail

APP_URL="${APP_URL:-http://localhost:8080}"
HEALTH_URL="${HEALTH_URL:-$APP_URL/actuator/health}"
ENV_FILE="${ENV_FILE:-.env}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-120}"

BUILD=true
CLEAN=false
FOLLOW_LOGS=false

usage() {
  cat <<'USAGE'
Uso: ./start.sh [opcoes]

Opcoes:
  --no-build          Sobe os containers sem rebuild da imagem.
  --clean             Executa docker compose down -v antes de subir.
  --logs              Abre docker compose logs -f app ao final.
  --wait-timeout N    Tempo maximo, em segundos, para aguardar a API. Padrao: 120.
  -h, --help          Mostra esta ajuda.

Variaveis opcionais:
  APP_URL             URL base da API. Padrao: http://localhost:8080
  HEALTH_URL          URL de healthcheck. Padrao: $APP_URL/actuator/health
  ENV_FILE            Arquivo de variaveis do Compose. Padrao: .env
USAGE
}

log() {
  printf '[start] %s\n' "$1"
}

fail() {
  printf '[start][erro] %s\n' "$1" >&2
  exit 1
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --no-build)
        BUILD=false
        ;;
      --clean)
        CLEAN=true
        ;;
      --logs)
        FOLLOW_LOGS=true
        ;;
      --wait-timeout)
        shift
        [[ $# -gt 0 ]] || fail "Informe um valor para --wait-timeout."
        WAIT_TIMEOUT_SECONDS="$1"
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        fail "Opcao desconhecida: $1. Use ./start.sh --help."
        ;;
    esac
    shift
  done
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Comando obrigatorio nao encontrado: $1"
}

detect_compose() {
  if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
    return
  fi

  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose)
    return
  fi

  fail "Docker Compose nao encontrado. Instale o plugin docker compose v2 ou docker-compose."
}

prepare_env_file() {
  if [[ -f "$ENV_FILE" ]]; then
    return
  fi

  [[ -f .env.example ]] || fail "Arquivo $ENV_FILE nao existe e .env.example nao foi encontrado."
  cp .env.example "$ENV_FILE"
  log "Arquivo $ENV_FILE criado a partir de .env.example. Revise os secrets antes de usar fora de desenvolvimento."
}

wait_for_api() {
  local deadline
  deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))

  log "Aguardando API responder em $HEALTH_URL por ate ${WAIT_TIMEOUT_SECONDS}s..."

  until curl -fsS "$HEALTH_URL" >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      printf '\n' >&2
      "${COMPOSE[@]}" ps >&2 || true
      "${COMPOSE[@]}" logs --tail=120 app >&2 || true
      fail "API nao ficou saudavel dentro do tempo limite."
    fi

    printf '.'
    sleep 3
  done

  printf '\n'
}

print_summary() {
  log "Sistema iniciado com sucesso."
  printf '\n'
  printf 'API:      %s\n' "$APP_URL"
  printf 'Health:   %s\n' "$HEALTH_URL"
  printf 'Swagger:  %s/swagger-ui.html\n' "$APP_URL"
  printf 'OpenAPI:  %s/v3/api-docs\n' "$APP_URL"
  printf 'Postgres: localhost:5432\n'
  printf '\n'
  printf 'Comandos uteis:\n'
  printf '  docker compose ps\n'
  printf '  docker compose logs -f app\n'
  printf '  docker compose down\n'
}

main() {
  parse_args "$@"
  require_command docker
  require_command curl
  detect_compose

  docker info >/dev/null 2>&1 || fail "Docker nao parece estar rodando."
  prepare_env_file

  if [[ "$CLEAN" == true ]]; then
    log "Removendo containers, rede e volumes anteriores..."
    "${COMPOSE[@]}" down -v --remove-orphans
  fi

  if [[ "$BUILD" == true ]]; then
    log "Subindo containers com build..."
    "${COMPOSE[@]}" up -d --build
  else
    log "Subindo containers sem build..."
    "${COMPOSE[@]}" up -d
  fi

  wait_for_api
  print_summary

  if [[ "$FOLLOW_LOGS" == true ]]; then
    "${COMPOSE[@]}" logs -f app
  fi
}

main "$@"
