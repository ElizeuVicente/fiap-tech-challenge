#!/usr/bin/env bash

set -Eeuo pipefail

NAMESPACE="${NAMESPACE:-oficina}"
API_DEPLOYMENT="${API_DEPLOYMENT:-oficina-api}"
API_SERVICE="${API_SERVICE:-oficina-api}"
LOAD_DEPLOYMENT="${LOAD_DEPLOYMENT:-oficina-load}"
LOAD_REPLICAS="${LOAD_REPLICAS:-20}"
RESET_REPLICAS="${RESET_REPLICAS:-2}"
WATCH_SECONDS="${WATCH_SECONDS:-180}"
HPA_MEMORY_TARGET="${HPA_MEMORY_TARGET:-50}"
DEFAULT_HPA_MEMORY_TARGET="${DEFAULT_HPA_MEMORY_TARGET:-75}"
RESTORE_HPA_TARGET_AFTER="${RESTORE_HPA_TARGET_AFTER:-true}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Comando obrigatorio nao encontrado: $1" >&2
    exit 1
  }
}

metrics_available() {
  kubectl top pods -n "${NAMESPACE}" >/dev/null 2>&1
}

cleanup() {
  kubectl delete deployment "${LOAD_DEPLOYMENT}" -n "${NAMESPACE}" --ignore-not-found=true >/dev/null 2>&1 || true

  if [[ "${RESTORE_HPA_TARGET_AFTER}" == "true" ]]; then
    kubectl patch hpa "${API_DEPLOYMENT}" -n "${NAMESPACE}" --type=json \
      -p="[{\"op\":\"replace\",\"path\":\"/spec/metrics/1/resource/target/averageUtilization\",\"value\":${DEFAULT_HPA_MEMORY_TARGET}}]" \
      >/dev/null 2>&1 || true
  fi
}

print_state() {
  kubectl get hpa -n "${NAMESPACE}"
  kubectl get pods -n "${NAMESPACE}" -l app="${API_DEPLOYMENT}"
  kubectl top pods -n "${NAMESPACE}"
}

require_command kubectl
require_command minikube

trap cleanup EXIT

echo "== Oficina Tech Challenge | Autoscaling =="
echo "NAMESPACE=${NAMESPACE}"
echo "LOAD_REPLICAS=${LOAD_REPLICAS}"
echo "RESET_REPLICAS=${RESET_REPLICAS}"
echo "WATCH_SECONDS=${WATCH_SECONDS}"
echo "HPA_MEMORY_TARGET=${HPA_MEMORY_TARGET}"
echo

echo "1/5 - Metrics Server"
if ! metrics_available; then
  minikube addons enable metrics-server >/dev/null
  kubectl rollout status deployment/metrics-server -n kube-system --timeout=180s
fi
kubectl top pods -n "${NAMESPACE}"
echo

echo "2/5 - Estado inicial"
kubectl patch hpa "${API_DEPLOYMENT}" -n "${NAMESPACE}" --type=json \
  -p="[{\"op\":\"replace\",\"path\":\"/spec/metrics/1/resource/target/averageUtilization\",\"value\":${DEFAULT_HPA_MEMORY_TARGET}}]" \
  >/dev/null
kubectl scale deployment "${API_DEPLOYMENT}" -n "${NAMESPACE}" --replicas="${RESET_REPLICAS}" >/dev/null
kubectl rollout status deployment/"${API_DEPLOYMENT}" -n "${NAMESPACE}" --timeout=180s
sleep 10
print_state
echo

echo "3/5 - Carga interna"
kubectl delete deployment "${LOAD_DEPLOYMENT}" -n "${NAMESPACE}" --ignore-not-found=true >/dev/null
kubectl create deployment "${LOAD_DEPLOYMENT}" -n "${NAMESPACE}" \
  --image=busybox:1.36 \
  --replicas="${LOAD_REPLICAS}" \
  -- /bin/sh -c "while true; do wget -q -O- http://${API_SERVICE}:8080/actuator/health >/dev/null; done" \
  >/dev/null
kubectl rollout status deployment/"${LOAD_DEPLOYMENT}" -n "${NAMESPACE}" --timeout=180s
kubectl get pods -n "${NAMESPACE}" -l app="${LOAD_DEPLOYMENT}"
echo

echo "4/5 - HPA"
kubectl patch hpa "${API_DEPLOYMENT}" -n "${NAMESPACE}" --type=json \
  -p="[{\"op\":\"replace\",\"path\":\"/spec/metrics/1/resource/target/averageUtilization\",\"value\":${HPA_MEMORY_TARGET}}]"
echo

echo "5/5 - Observacao"
end_time=$((SECONDS + WATCH_SECONDS))
while ((SECONDS < end_time)); do
  date '+%H:%M:%S'
  print_state
  echo
  sleep 15
done

echo "Finalizado"
