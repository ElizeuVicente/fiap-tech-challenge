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
terraform plan
terraform apply
```

Para uma imagem publicada:

```bash
terraform apply -var='app_image=ghcr.io/seu-org/seu-repo/oficina-tech-challenge:tag-imutavel'
```

## Observações

Forneça `TF_VAR_jwt_secret`, `TF_VAR_webhook_secret` e `TF_VAR_postgres_password` pelo ambiente local ou por um `terraform.tfvars` **não versionado**; evite segredos na linha de comando e no histórico do shell. Secrets do Terraform ainda ficam no state. Este Terraform é apenas para laboratório Kubernetes local; o deploy cloud da Fase 3 deve usar o repositório de infraestrutura AWS e state protegido.
