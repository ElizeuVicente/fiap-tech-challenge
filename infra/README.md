# Infraestrutura Terraform

Este diretório provisiona os recursos Kubernetes necessários para a Fase 2 em um cluster já acessível pelo `kubeconfig`: namespace, ConfigMap, Secret, PostgreSQL, Deployment da API, Services e HPA.

## Pré-requisitos

- Terraform 1.6+
- Cluster Kubernetes acessível via `~/.kube/config`
- Metrics Server instalado para o HPA reportar CPU/memória
- Imagem Docker da API disponível no cluster, por exemplo `oficina-tech-challenge:latest` em kind/minikube ou uma imagem publicada no GHCR

## Comandos

```bash
terraform init
terraform plan \
  -var='jwt_secret=64656661756c747365637265746b65796d75737462657374726f6e6765727468616e74686973313233343536' \
  -var='webhook_secret=dev-webhook-secret-change-me' \
  -var='postgres_password=password'
terraform apply \
  -var='jwt_secret=64656661756c747365637265746b65796d75737462657374726f6e6765727468616e74686973313233343536' \
  -var='webhook_secret=dev-webhook-secret-change-me' \
  -var='postgres_password=password'
```

Para uma imagem publicada:

```bash
terraform apply \
  -var='app_image=ghcr.io/seu-org/seu-repo/oficina-tech-challenge:latest' \
  -var='jwt_secret=...' \
  -var='webhook_secret=...' \
  -var='postgres_password=...'
```

## Observações

Secrets do Terraform ficam no state. Para produção, armazene o state em backend remoto com criptografia e controle de acesso. Para o escopo acadêmico, os valores acima são demonstrativos e devem ser trocados antes de uma demonstração pública.
