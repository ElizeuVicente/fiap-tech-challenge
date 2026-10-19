# RFC 001 — Arquitetura de nuvem da Fase 3

Status: proposta implementada **em código**, ainda sem validação em AWS. Data: 19/10/2026.

## Problema e escopo

O edital exige gateway, autenticação por CPF em Function Serverless, banco gerenciado, Kubernetes escalável, CI/CD por ambiente e observabilidade demonstrável. A entrega anterior tinha apenas ambiente local. Esta RFC define a menor topologia AWS capaz de satisfazer os componentes centrais durante uma janela curta do Learner Lab, com teto de US$ 25 e sem links ativos antes da autorização do aluno.

## Decisão proposta

- AWS us-east-1: API Gateway HTTP, Lambda Java 17, EKS com um node group, ALB interno e RDS PostgreSQL privado.
- Homologação e produção compartilham VPC/EKS/RDS por economia, mas possuem namespaces, schemas PostgreSQL, Lambdas CPF e chaves JWT distintos.
- O gateway usa VPC Link para o ALB e authorizers Lambda separados por ambiente. A API repete autorização por papel e posse de OS.
- Flyway é a única fonte de DDL; `ddl-auto=validate`. Migrations rodam no schema do ambiente durante o primeiro start da API.
- Prometheus, Grafana, Alertmanager, Loki/Alloy e Tempo ficam internos ao cluster; retenção e volumes efêmeros são adequados apenas à demonstração acadêmica.
- Terraform mantém três states S3 separados por responsabilidade, com lockfile, versionamento e criptografia. Credenciais temporárias da AWS Academy entram somente em GitHub Environments protegidos.

## Alternativas rejeitadas

Dois clusters EKS e dois RDS aumentariam isolamento, porém duplicariam custo fixo e ameaçariam o teto. NAT Gateway simplificaria a posição dos nós em subnets privadas, mas acrescentaria custo fixo; a janela acadêmica usa nós públicos sem SSH e restringe o endpoint Kubernetes por CIDR. PostgreSQL em pod/Compose não satisfaz o requisito de banco gerenciado. Kong local não substitui a demonstração em nuvem pedida pelo parecer.

## Segurança e consequências

O RDS não tem IP público. O ALB é interno; a única entrada de aplicação é o gateway HTTPS. Chaves JWT distintas evitam aceitação cruzada entre ambientes. Senhas e chaves entram no state Terraform **mesmo marcadas como sensitive**; por isso o bucket não pode ser público e precisa de acesso IAM restrito. Recursos de observabilidade usam `emptyDir`: se o pod reiniciar, parte dos dados desaparece. A topologia tem um nó por padrão, logo não promete alta disponibilidade corporativa; serve para comprovar escalabilidade configurada e fluxo cloud em janela de avaliação.

## Critérios de aceite

PRs verdes e `terraform validate` não bastam. Aceitar somente após: Terraform plan revisado, recursos AWS inventariados, teste externo de CPF/JWT/OS, HPA com métricas, dashboards com séries reais, alerta recebido, logs e trace correlacionados, pipelines homolog/prod executados e custo observado dentro do limite. Se o avaliador exigir links ativos após a janela, negociar prazo/aceite antes de desmontar.
