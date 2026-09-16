# Plano de Versionamento para Fechamento da Fase 3

Este arquivo contém apenas entregáveis que devem estar no Git. Configurações de GitHub, secrets, vídeos e execução de ambiente não pertencem a commits.

## Próximos commits obrigatórios

1. Function serverless: manifesto Knative com imagem imutável por SHA e referências a secrets.
2. Observabilidade: Collector/Tempo, dashboard de negócio e alertas de latência/falha.
3. Gateway: CORS, access log, rate limit e JWT sem segredos em YAML.
4. Qualidade: testes de JWT, histórico, idempotência, métricas e Flyway/PostgreSQL.
5. Infra: Terraform do alvo escolhido, manifests e rollback/smoke test.

## Critério de aceite

- Sem `.env`, tokens, senhas, kubeconfig, `tfstate` ou chave JWT.
- README com validação local.
- Testes, Terraform ou Compose passam antes do push.
- Deploy usa SHA imutável, não `latest`.
