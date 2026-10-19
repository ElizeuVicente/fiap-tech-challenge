# ADR 001 — Topologia compartilhada e isolamento lógico

Status: aceita para a janela acadêmica; sujeita à prova de custo e permissões AWS.

Contexto: o PDF exige infraestrutura gerenciada e dois ambientes, mas o laboratório tem créditos limitados. Dois EKS e dois RDS podem consumir a reserva antes da avaliação.

Decisão: usar uma VPC, um EKS, um ALB e um RDS. `oficina-homolog` e `oficina-prod` são namespaces distintos; `oficina_homolog` e `oficina_prod` são schemas distintos; as Lambdas e chaves JWT também são distintas. Um único recurso Terraform compartilhado não deve ser aplicado concorrentemente por duas branches. Alterações de rede/banco compartilhados são promovidas em `main` depois de validação em PR; aplicações e autenticação devem demonstrar deploy por ambiente.

Consequências: reduz custo fixo e estado duplicado, mas ambientes compartilham capacidade, rede e domínio de falha. Não é isolamento adequado para dados reais ou produção empresarial. Com um único nó, HPA pode aumentar pods somente se houver CPU/memória disponível; aumentar nó muda a estimativa. A evolução futura é separar contas, VPCs e bancos.
