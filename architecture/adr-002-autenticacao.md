# ADR 002 — Autenticação por CPF e autorização em camadas

Status: aceita em código; integração cloud pendente.

Contexto: clientes precisam consultar apenas ordens próprias, enquanto administradores operam a oficina. CPF não é uma senha: a validação por CPF isolada atende o contrato acadêmico, mas não é autenticação forte para dados reais.

Decisão: Lambda consulta CPF e status `ATIVO` no schema correspondente e emite JWT de 15 minutos com `iss`, `aud`, `role`, CPF e expiração. API Gateway usa Lambda authorizer para verificar assinatura e claims nas rotas privadas. A API Spring valida de novo e verifica papel e posse da OS por CPF/ID. Login administrativo segue por senha e JWT próprio; a rota de login não exige token prévio. Cada ambiente tem chave HMAC diferente.

Consequências: há defesa em profundidade e testes para cliente/admin/terceiro. Chaves simétricas precisam de rotação coordenada e state protegido. CPF e JWT não devem ser registrados em logs. Para uso real, substituir o fator único CPF por desafio adicional/identidade federada; o edital não exige essa expansão.
