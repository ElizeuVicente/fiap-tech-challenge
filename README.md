# Oficina Tech Challenge - Sistema de Oficina

Backend para gestao operacional de uma oficina mecanica, desenvolvido para o Tech Challenge FIAP 14SOAT. O projeto evolui o MVP da Fase 1 para a Fase 2, com abertura completa de ordem de servico, consulta publica de status, notificacao externa de aprovacao/recusa de orcamento, monitoramento, containerizacao, Kubernetes, Terraform e CI/CD.

## Proposito

O sistema centraliza o ciclo de atendimento de uma oficina: cadastro de cliente e veiculo, abertura da ordem de servico, diagnostico, composicao de orcamento, aprovacao ou recusa, baixa de estoque, execucao, finalizacao e entrega do veiculo.

O objetivo tecnico e demonstrar uma aplicacao Spring Boot com dominio rico, regras de negocio protegidas nas entidades, API REST documentada, testes automatizados e infraestrutura reproduzivel para execucao local ou em Kubernetes.

## Arquitetura

A aplicacao e um monolito modular orientado a DDD. As responsabilidades estao separadas por camadas:

```text
src/main/java/com/oficina/tech_challenge/
|-- domain/
|   |-- entities/        Entidades e regras de negocio
|   |-- valueobjects/    Objetos de valor como CPF/CNPJ e valores monetarios
|   `-- repositories/    Contratos de persistencia
|-- application/
|   |-- services/        Casos de uso e orquestracao transacional
|   |-- dtos/            Dados de entrada e saida da aplicacao
|   `-- interfaces/      Contratos de servicos
|-- presentation/
|   |-- controllers/     Endpoints REST
|   |-- dtos/            DTOs da API
|   `-- handlers/        Tratamento padronizado de erros
`-- infrastructure/
    `-- security/        JWT, filtros e configuracao de seguranca
```

Fluxo principal:

```text
Cliente/API -> Controller -> Application Service -> Domain Entity/Value Object -> Repository -> PostgreSQL
```

Ponto arquitetural importante: as entidades de dominio ainda usam anotacoes JPA. Em Clean Architecture/Hexagonal estrita, dominio e modelo de persistencia seriam separados. Nesta entrega academica, a decisao foi manter esse acoplamento controlado para preservar escopo e reduzir risco, com a divida documentada em `docs/HANDOFF.md` e `docs/registro-alteracoes.md`.

## Funcionalidades

- Autenticacao JWT para rotas administrativas.
- Consulta publica de OS por cliente.
- Abertura completa de OS com cliente, veiculo, servicos e pecas no mesmo payload.
- Fluxo de status protegido pela entidade `OrdemServico`.
- Geracao de orcamento e baixa de estoque ao aprovar.
- Webhook externo para aprovacao ou recusa de orcamento com idempotencia.
- Listagem operacional de ordens por prioridade.
- Monitoramento de tempo medio e totais.
- Validacao de CPF/CNPJ com digito verificador.
- Aceite de placa brasileira antiga e Mercosul, com normalizacao.
- Swagger/OpenAPI, Postman collection, Docker Compose, Kubernetes, Terraform e GitHub Actions.

## Stack

- Java 17
- Spring Boot 3.3.4
- Spring Data JPA / Hibernate
- Spring Security + JWT
- PostgreSQL 15
- Maven Wrapper
- JUnit 5 + JaCoCo
- Docker e Docker Compose
- Kubernetes manifests
- Terraform com provider Kubernetes

## Pre-requisitos

Para executar com Docker:

- Docker 20.10+
- Docker Compose v2+
- `curl`

Para desenvolvimento local sem container da aplicacao:

- Java 17+
- Maven 3.9+ ou `./mvnw`
- PostgreSQL 15+ ou container PostgreSQL

Para Kubernetes/Terraform:

- `kubectl`
- Um cluster Kubernetes local ou remoto, como minikube, kind, Docker Desktop, EKS ou outro
- `terraform`

## Inicio rapido

O caminho recomendado e usar o script:

```bash
./start.sh
```

O script:

1. verifica Docker, Docker Compose e `curl`;
2. cria `.env` a partir de `.env.example`, se ainda nao existir;
3. sobe PostgreSQL e API com `docker compose up -d --build`;
4. espera o healthcheck da API em `/actuator/health`;
5. imprime os links e comandos uteis.

URLs apos subir:

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`
- PostgreSQL: `localhost:5432`

Opcoes do script:

```bash
./start.sh              # sobe com build e aguarda healthcheck
./start.sh --no-build   # sobe sem rebuild da imagem
./start.sh --clean      # remove containers e volumes antes de subir
./start.sh --logs       # abre logs da API ao final
./start.sh --help       # mostra ajuda
```

Execucao manual equivalente:

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
curl http://localhost:8080/actuator/health
```

Para parar:

```bash
docker compose down
```

Para parar e remover o banco local:

```bash
docker compose down -v
```

## Variaveis de ambiente

O `.env.example` contem valores de desenvolvimento:

```env
SECURITY_JWT_SECRET=64656661756c747365637265746b65796d75737462657374726f6e6765727468616e74686973313233343536
EXTERNAL_WEBHOOK_SECRET=dev-webhook-secret-change-me
JAVA_OPTS=-XX:+UseG1GC -XX:MaxRAMPercentage=75.0
```

Em ambientes reais, altere `SECURITY_JWT_SECRET` e `EXTERNAL_WEBHOOK_SECRET`.

## Autenticacao

As rotas administrativas exigem JWT. Rotas publicas:

- `POST /api/auth/**`
- `GET /api/ordens-servico/cliente/**`
- `POST /api/ordens-servico/{id}/orcamento/notificacoes`
- Swagger/OpenAPI
- `/actuator/health`

Criar usuario:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"senha123456"}'
```

Fazer login:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"senha123456"}'
```

Usar token:

```bash
TOKEN="cole-o-token-aqui"

curl http://localhost:8080/api/clientes \
  -H "Authorization: Bearer $TOKEN"
```

## Endpoints principais

Autenticacao:

```text
POST /api/auth/register
POST /api/auth/login
```

Clientes e veiculos:

```text
GET    /api/clientes
POST   /api/clientes
GET    /api/clientes/{id}
PUT    /api/clientes/{id}
DELETE /api/clientes/{id}
GET    /api/clientes/{id}/veiculos
POST   /api/clientes/{id}/veiculos
PUT    /api/clientes/{id}/veiculos/{veiculoId}
DELETE /api/clientes/{id}/veiculos/{veiculoId}
```

Servicos:

```text
GET    /api/servicos
POST   /api/servicos
GET    /api/servicos/{id}
PUT    /api/servicos/{id}
DELETE /api/servicos/{id}
```

Pecas:

```text
GET    /api/pecas
POST   /api/pecas
GET    /api/pecas/{id}
PUT    /api/pecas/{id}
DELETE /api/pecas/{id}
PATCH  /api/pecas/{id}/estoque
```

Ordens de servico:

```text
GET    /api/ordens-servico
GET    /api/ordens-servico?operacional=true
POST   /api/ordens-servico
GET    /api/ordens-servico/{id}
GET    /api/ordens-servico/{id}/status
GET    /api/ordens-servico/cliente/{cpfCnpj}
POST   /api/ordens-servico/{id}/itens
PATCH  /api/ordens-servico/{id}/diagnostico
PATCH  /api/ordens-servico/{id}/orcamento
POST   /api/ordens-servico/{id}/orcamento/notificacoes
POST   /api/ordens-servico/{id}/status
PATCH  /api/ordens-servico/{id}/aprovar
PATCH  /api/ordens-servico/{id}/finalizar
PATCH  /api/ordens-servico/{id}/entregar
GET    /api/ordens-servico/monitoramento
```

## Exemplo de teste via API

1. Suba o projeto:

```bash
./start.sh
```

2. Crie usuario e obtenha token:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"senha123456"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"senha123456"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
```

3. Cadastre um servico:

```bash
SERVICO_ID=$(curl -s -X POST http://localhost:8080/api/servicos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Troca de oleo","descricao":"Troca de oleo do motor","preco":180.00}' \
  | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
```

4. Cadastre uma peca:

```bash
PECA_ID=$(curl -s -X POST http://localhost:8080/api/pecas \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Filtro de oleo","descricao":"Filtro de oleo do motor","preco":45.00,"quantidadeEstoque":10}' \
  | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
```

5. Abra uma OS completa:

```bash
OS_ID=$(curl -s -X POST http://localhost:8080/api/ordens-servico \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"cliente\": {
      \"nome\": \"Maria Cliente\",
      \"cpfCnpj\": \"98765432100\",
      \"telefone\": \"11999999999\"
    },
    \"veiculo\": {
      \"placa\": \"ABC-1234\",
      \"marca\": \"Fiat\",
      \"modelo\": \"Uno\",
      \"ano\": 2020
    },
    \"servicos\": [{ \"servicoId\": \"$SERVICO_ID\" }],
    \"pecas\": [{ \"pecaId\": \"$PECA_ID\", \"quantidade\": 1 }]
  }" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
```

6. Consulte status:

```bash
curl http://localhost:8080/api/ordens-servico/$OS_ID/status \
  -H "Authorization: Bearer $TOKEN"
```

7. Simule notificacao externa de aprovacao:

```bash
curl -X POST http://localhost:8080/api/ordens-servico/$OS_ID/orcamento/notificacoes \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Secret: dev-webhook-secret-change-me" \
  -d '{
    "decisao": "APROVADO",
    "origem": "sistema-externo",
    "dataHora": "2026-06-17T10:00:00",
    "identificadorExterno": "ext-001"
  }'
```

Tambem ha uma collection pronta em `docs/postman/oficina-tech-challenge-fase2.postman_collection.json`.

## Testes automatizados

Rodar suite completa com verificacao de cobertura:

```bash
./mvnw verify
```

Rodar apenas testes:

```bash
./mvnw test
```

Relatorio JaCoCo:

```bash
xdg-open target/site/jacoco/index.html
```

Observacao: os testes de integracao ainda usam H2. A evolucao para Testcontainers com PostgreSQL real esta registrada como melhoria recomendada no handoff.

## Docker

O `Dockerfile` e multi-stage:

- build com `maven:3.9.6-eclipse-temurin-17`;
- runtime com `eclipse-temurin:17-jre-alpine`;
- usuario nao-root `oficina`;
- healthcheck em `/actuator/health`;
- `JAVA_OPTS` configuravel.

Comandos uteis:

```bash
docker compose ps
docker compose logs -f app
docker compose logs -f db
docker compose down
docker build -t oficina-tech-challenge:test .
```

## Kubernetes

Os manifests ficam em `k8s/`.

Aplicar em um cluster ja configurado no `kubectl`:

```bash
kubectl apply -f k8s/
kubectl get pods -n oficina
kubectl get svc -n oficina
kubectl port-forward svc/oficina-api 8080:8080 -n oficina
```

Testar:

```bash
curl http://localhost:8080/actuator/health
```

O HPA depende do Metrics Server. Em minikube:

```bash
minikube addons enable metrics-server
```

## Terraform

O Terraform em `infra/` usa o provider Kubernetes. Ele nao cria uma conta AWS nem cria um cluster por conta propria; ele aplica recursos Kubernetes no cluster apontado pelo seu `kubeconfig`.

Para usar localmente:

```bash
cd infra
terraform init
terraform plan \
  -var='jwt_secret=troque-este-jwt-secret' \
  -var='webhook_secret=troque-este-webhook-secret' \
  -var='postgres_password=troque-esta-senha'
terraform apply \
  -var='jwt_secret=troque-este-jwt-secret' \
  -var='webhook_secret=troque-este-webhook-secret' \
  -var='postgres_password=troque-esta-senha'
```

Para AWS, voce precisaria de uma conta AWS apenas se decidir criar ou usar um cluster EKS. Este repositorio, neste momento, entrega recursos Kubernetes genericos.

## CI/CD

O workflow `.github/workflows/ci-cd.yml` executa:

- checkout;
- setup Java 17;
- `./mvnw verify`;
- build da imagem Docker;
- push para GHCR em pushes;
- deploy em Kubernetes quando `KUBE_CONFIG` estiver configurado como secret base64.

## Documentacao complementar

- Handoff tecnico: `docs/HANDOFF.md`
- Guia para agentes: `docs/AGENTS.md`
- Registro de alteracoes: `docs/registro-alteracoes.md`
- Infraestrutura: `infra/README.md`
- Analise de vulnerabilidades: `ANALISE_VULNERABILIDADES.md`
- Collection Postman: `docs/postman/oficina-tech-challenge-fase2.postman_collection.json`

## Troubleshooting

Porta 8080 ocupada:

```bash
lsof -i :8080
docker compose down
```

Banco indisponivel:

```bash
docker compose ps
docker compose logs db
```

API nao sobe:

```bash
docker compose logs -f app
curl http://localhost:8080/actuator/health
```

Token JWT invalido:

- confirme que o header e `Authorization: Bearer <token>`;
- gere novo token em `/api/auth/login`;
- verifique se o `.env` tem `SECURITY_JWT_SECRET`.
