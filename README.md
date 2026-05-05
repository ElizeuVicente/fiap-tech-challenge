# Oficina Tech Challenge - Sistema Integrado de Serviços

MVP do Sistema de Gestão de Oficina Mecânica desenvolvido com Spring Boot, PostgreSQL e JPA, seguindo os princípios de Domain-Driven Design (DDD).

## 🚀 Funcionalidades Principais

- **Gestão de Ordens de Serviço**: Fluxo completo desde o recebimento até a entrega do veículo.
- **Orçamento Automático**: Cálculo de valor total baseado em serviços e peças.
- **Controle de Estoque**: Baixa automática de peças ao aprovar um orçamento.
- **Segurança**: Autenticação via JWT para rotas administrativas.
- **Documentação**: API documentada via Swagger/OpenAPI.

## 🛠️ Stack Técnica

- **Linguagem**: Java 17+
- **Framework**: Spring Boot 3+
- **Banco de Dados**: PostgreSQL
- **Segurança**: Spring Security + JWT
- **Build & Execução**: Maven & Docker

## 📦 Como Executar

### Pré-requisitos
- Docker e Docker Compose instalados.

### Execução Facilitada (Script)
1. Clone o repositório.
1. Certifique-se de que o Docker está rodando.
2. Na raiz do projeto, execute:
   ```bash
   ./start.sh
   ```
   *O script irá subir o banco de dados, aguardar a prontidão e iniciar a API.*

### Execução Manual via Docker Compose
1. Na raiz do projeto, execute:
   ```bash
   docker-compose up --build
   ```
2. A aplicação estará disponível em `http://localhost:8080`.

### Documentação da API (Swagger)
Acesse: `http://localhost:8080/swagger-ui.html`

## 🔐 Autenticação

Para acessar as rotas protegidas:
1. Registre um usuário em `/api/auth/register`.
2. Faça login em `/api/auth/login` para obter o Token JWT.
3. Utilize o token no Header: `Authorization: Bearer <seu_token>`.

## 🏗️ Arquitetura (DDD)

- **domain**: Entidades ricas (`OrdemServico`, `Cliente`), Value Objects (`CpfCnpj`) e Repositórios.
- **application**: Casos de uso e orquestração de serviços.
## 🛡️ Análise de Segurança e Qualidade

O sistema foi desenvolvido seguindo as melhores práticas de OWASP e Clean Code:
- **JWT (Stateless)**: Autenticação robusta para todas as APIs administrativas (`/api/auth/**`).
- **Validação de Input**: VOs dedicados para CPF/CNPJ e Placas garantem a integridade dos dados.
- **DDD**: Separação clara entre regra de negócio e infraestrutura, facilitando testes e evolução.
- **Monitoramento**: Endpoint dedicado para acompanhar o tempo médio de execução dos serviços.

## 💎 Justificativa do Banco de Dados: PostgreSQL
Escolhemos o **PostgreSQL** pela sua robustez em transações ACID, suporte nativo a tipos complexos e excelente integridade referencial, essenciais para gerir orçamentos financeiros e estoque físico de forma segura.
