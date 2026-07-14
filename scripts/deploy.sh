#!/usr/bin/env bash

set -Eeuo pipefail

IMAGE_REPOSITORY="${IMAGE_REPOSITORY:-oficina-tech-challenge}"
IMAGE_TAG="${IMAGE_TAG:-$(date +%Y%m%d%H%M%S)}"
IMAGE_NAME="${IMAGE_NAME:-${IMAGE_REPOSITORY}:${IMAGE_TAG}}"
NAMESPACE="${NAMESPACE:-oficina}"

echo "Building image ${IMAGE_NAME}..."
docker build -t "${IMAGE_NAME}" .

echo "Loading image ${IMAGE_NAME} into minikube..."
minikube image load "${IMAGE_NAME}"

echo "Applying Kubernetes manifests..."
kubectl apply -f k8s/

echo "Updating API deployment image..."
kubectl set image deployment/oficina-api oficina-api="${IMAGE_NAME}" -n "${NAMESPACE}"
kubectl rollout status deployment/oficina-api -n "${NAMESPACE}" --timeout=240s

kubectl get pods -n "${NAMESPACE}"
kubectl get hpa -n "${NAMESPACE}"
kubectl get svc -n "${NAMESPACE}"
