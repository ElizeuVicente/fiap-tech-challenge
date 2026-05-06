# Oficina Tech Challenge - Sistema Integrado de Serviços

MVP do Sistema de Gestão de Oficina Mecânica desenvolvido com Spring Boot, PostgreSQL e JPA, seguindo os princípios de Domain-Driven Design (DDD). Este sistema foi implementado como fase 1 de um projeto de FIAP focado em boas práticas de qualidade de software e arquitetura escalável.

## Visão Geral

O Oficina Tech Challenge é uma solução backend monolítica que centraliza a gestão operacional de uma oficina mecânica de médio porte. O sistema implementa um fluxo completo de atendimento, desde a recepção do veículo até a entrega, incluindo diagnóstico, orçamento, execução de serviços e controle de estoque de peças.

A arquitetura segue os princípios de Domain-Driven Design (DDD), separando claramente as responsabilidades entre camadas de domínio, aplicação e infraestrutura, facilitando testes, manutenção e evolução do sistema.

## Funcionalidades Principais

### Gestão de Ordens de Serviço

Implementa o fluxo completo de atendimento com os seguintes estados:
- Recebida: Ordem registrada no sistema
- Em diagnóstico: Mecânico avaliando o veículo
- Aguardando aprovação: Orçamento enviado ao cliente
- Em execução: Serviços sendo realizados
- Finalizada: Serviços concluídos, veículo pronto para entrega
- Entregue: Veículo retirado pelo cliente

### Orçamento Automático

Cálculo automático de valor total baseado em:
- Serviços solicitados com valores pré-configurados
- Peças necessárias com custos atualizáveis
- Margem de lucro configurável por serviço

### Controle de Estoque

Gerenciamento dinâmico de peças e insumos com:
- Rastreamento de quantidade em estoque
- Baixa automática de peças ao aprovar orçamento
- Alertas de estoque baixo
- Histórico de movimentação

### Gestão de Clientes e Veículos

CRUD completo com:
- Registro de clientes por CPF/CNPJ (validado)
- Cadastro de veículos associados ao cliente (placa, marca, modelo, ano)
- Histórico de serviços e orçamentos por veículo
- Consultas por cliente ou identificador do veículo

### Segurança

Implementação robusta de autenticação e autorização:
- Autenticação via JWT (JSON Web Tokens)
- Proteção de endpoints administrativos
- Validação de dados sensíveis (CPF, CNPJ, placas)

## Stack Técnica

### Linguagem e Framework
- Linguagem: Java 17
- Framework: Spring Boot 3.3.4
- Build Tool: Maven 3.9.6

### Banco de Dados
- SGBD: PostgreSQL 15
- Driver: PostgreSQL JDBC 42.7.10
- ORM: Hibernate 6.5.3 com Spring Data JPA

### Segurança
- Spring Security 6.3.3
- JWT: JJWT 0.12.5 (jjwt-api, jjwt-impl, jjwt-jackson)
- Validação: Jakarta Bean Validation

### Documentação e Testes
- API Doc: Springdoc OpenAPI 2.6.0
- Testes: JUnit 5 via Spring Boot Test
- Coverage: JaCoCo 0.8.11 (mínimo 80% nos domínios críticos)
- Test Database: H2 Database (escopo de teste)

### Containerização

- Docker Compose v5.0.2+
- Imagem Build: maven:3.9.6-eclipse-temurin-17
- Imagem Runtime: eclipse-temurin:17-jre

## Pré-requisitos

### Requisitos Obrigatórios

- Docker versão 20.10+
- Docker Compose versão 2.0+
- Acesso à internet para download de imagens Docker

### Requisitos Opcionais (Desenvolvimento Local)

- Java 17+
- Maven 3.9.6+
- PostgreSQL 15+ (se executar sem Docker)

## Como Executar

### Opção 1: Docker Compose (Recomendado)

Execução simplificada com todos os serviços containerizados:

```bash
docker compose up --build
```

Este comando:
1. Constrói a imagem Docker da aplicação
2. Inicia um container PostgreSQL 15 com healthcheck
3. Aguarda a prontidão do banco de dados
4. Inicia o container da aplicação na porta 8080

A aplicação estará disponível em http://localhost:8080 após inicialização completa.

### Opção 2: Script de Inicialização

Para ambiente de produção ou com configurações customizadas:

```bash
./start.sh
```

O script executa:
1. Verificação de disponibilidade do Docker
2. Limpeza de containers e volumes anteriores
3. Build e inicialização dos serviços
4. Validação de conectividade da API

### Opção 3: Execução Local com Maven

Para desenvolvimento com reload automático:

```bash
# Terminal 1 - Iniciar banco de dados
docker run -d \
  --name oficina-postgres \
  -e POSTGRES_DB=oficina \
  -e POSTGRES_USER=user \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 \
  postgres:15

# Terminal 2 - Executar aplicação
mvn spring-boot:run
```

## Documentação da API

### Acesso ao Swagger

A documentação interativa da API é acessível em:

http://localhost:8080/swagger-ui.html

A especificação OpenAPI em formato JSON:

http://localhost:8080/v3/api-docs

### Endpoints Principais

#### Autenticação

```
POST /api/auth/register
  Body: { "username": "string", "password": "string" }
  Response: 201 Created

POST /api/auth/login
  Body: { "username": "string", "password": "string" }
  Response: 200 OK { "token": "jwt_token_aqui" }
```

#### Clientes e Veículos

```
GET    /api/clientes                         - Listar todos os clientes
POST   /api/clientes                         - Criar novo cliente
GET    /api/clientes/{id}                    - Obter cliente por ID
PUT    /api/clientes/{id}                    - Atualizar cliente
DELETE /api/clientes/{id}                    - Deletar cliente

GET    /api/clientes/{id}/veiculos           - Listar veículos de um cliente
POST   /api/clientes/{id}/veiculos           - Adicionar veículo ao cliente
PUT    /api/clientes/{id}/veiculos/{vId}      - Atualizar veículo
DELETE /api/clientes/{id}/veiculos/{vId}      - Remover veículo
```

#### Serviços

```
GET    /api/servicos                         - Listar todos os serviços
POST   /api/servicos                         - Cadastrar novo serviço
GET    /api/servicos/{id}                    - Obter serviço por ID
PUT    /api/servicos/{id}                    - Atualizar serviço
DELETE /api/servicos/{id}                    - Remover serviço
```

#### Peças e Insumos

```
GET    /api/pecas                            - Listar todas as peças
POST   /api/pecas                            - Cadastrar nova peça
GET    /api/pecas/{id}                       - Obter peça por ID
PUT    /api/pecas/{id}                       - Atualizar peça
DELETE /api/pecas/{id}                       - Remover peça
PATCH  /api/pecas/{id}/estoque               - Atualizar quantidade (abater)
```

#### Ordens de Serviço (Fluxo RESTful)

```
GET    /api/ordens-servico                   - Listar todas as ordens
POST   /api/ordens-servico                   - Abrir nova OS (JSON Body)
GET    /api/ordens-servico/{id}              - Obter detalhes da OS
GET    /api/ordens-servico/cliente/{cpfCnpj} - Consultar OSs por Cliente

POST   /api/ordens-servico/{id}/itens        - Adicionar serviços/peças
PATCH  /api/ordens-servico/{id}/orcamento    - Gerar orçamento (calcula valores)
PATCH  /api/ordens-servico/{id}/diagnostico  - Registrar diagnóstico
PATCH  /api/ordens-servico/{id}/aprovar      - Aprovar orçamento (baixa estoque)
PATCH  /api/ordens-servico/{id}/finalizar    - Concluir execução
PATCH  /api/ordens-servico/{id}/entregar     - Registrar entrega ao cliente
```

#### Monitoramento

```
GET    /api/ordens-servico/monitoramento      - Métricas de tempo médio e totais
```

## Autenticação e Autorização

### Fluxo de Autenticação

1. Registro de Usuário

   Criar novo usuário com credenciais:
   ```bash
   curl -X POST http://localhost:8080/api/auth/register \
     -H "Content-Type: application/json" \
     -d '{"username": "usuario_teste", "password": "senha_segura_123"}'
   ```

2. Login

   Obter token JWT válido:
   ```bash
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username": "usuario_teste", "password": "senha_segura_123"}'
   ```

   Resposta:
   ```json
   {
     "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     "expiresIn": 3600
   }
   ```

3. Uso do Token

   Incluir token em requisições protegidas:
   ```bash
   curl -X GET http://localhost:8080/api/clientes \
     -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
   ```

### Configuração de Segurança

O arquivo `application.properties` contém:

- SECURITY_JWT_SECRET: Chave secreta para assinatura de tokens (configurável via variável de ambiente)
- SPRING_JPA_HIBERNATE_DDL_AUTO: Controla criação/atualização de schema (update)

## Validações e Regras de Negócio

### Value Objects

Implementados como objetos de valor imutáveis para garantir integridade de dados:

#### CpfCnpj

- Valida formato de CPF (11 dígitos) ou CNPJ (14 dígitos)
- Calcula dígito verificador
- Rejeita CPF/CNPJ inválidos na criação

#### Placa

- Valida formato brasileiro (AAA-9999 ou AAA9A99)
- Normaliza para formato padrão
- Garante unicidade no contexto do veículo

#### Valor Monetário

- Armazena em BigDecimal para precisão financeira
- Valida valores não-negativos
- Suporta operações de soma e subtração

### Validações de Entrada

Todas as requisições são validadas:

- Campos obrigatórios presentes
- Comprimento de strings dentro dos limites
- Formatos esperados (email, telefone, etc.)
- Integridade referencial (chaves estrangeiras)

### Tratamento de Erros

Respostas de erro standardizadas:

```json
{
  "timestamp": "2026-05-05T10:45:56Z",
  "status": 400,
  "error": "Validação de Entrada",
  "message": "CPF inválido: formato não reconhecido",
  "path": "/api/clientes"
}
```

Códigos HTTP utilizados:

- 200 OK: Sucesso em operações GET/PUT
- 201 Created: Recurso criado com sucesso (POST)
- 204 No Content: Sucesso em DELETE
- 400 Bad Request: Validação ou lógica de negócio falhou
- 401 Unauthorized: Token ausente ou inválido
- 403 Forbidden: Usuário sem permissão
- 404 Not Found: Recurso não encontrado
- 500 Internal Server Error: Erro não tratado

## Arquitetura (DDD)

### Estrutura de Diretórios

```
src/main/java/com/oficina/tech_challenge/
├── domain/
│   ├── entities/           # Entidades ricas do domínio
│   │   ├── OrdemServico
│   │   ├── Cliente
│   │   ├── Veiculo
│   │   └── Peca
│   ├── valueobjects/       # Value Objects imutáveis
│   │   ├── CpfCnpj
│   │   ├── Placa
│   │   └── Valor
│   └── repositories/       # Interfaces de persistência
│       ├── OrdemServicoRepository
│       ├── ClienteRepository
│       └── VeiculoRepository
├── application/
│   ├── services/           # Casos de uso da aplicação
│   │   ├── GerenciadorOrdemServico
│   │   ├── GerenciadorCliente
│   │   └── GerenciadorEstoque
│   ├── dtos/               # Data Transfer Objects
│   │   ├── OrdemServicoDTO
│   │   ├── ClienteDTO
│   │   └── VeiculoDTO
│   └── interfaces/         # Contratos de serviço
├── infrastructure/
│   ├── persistence/        # Implementações JPA
│   │   └── repositories/
│   └── security/           # Configuração de segurança
│       └── JwtTokenProvider
└── api/
    ├── controllers/        # Endpoints REST
    │   ├── OrdemServicoController
    │   ├── ClienteController
    │   └── VeiculoController
    └── config/             # Configuração da aplicação
        └── SecurityConfig
```

### Camadas

#### Domain (Domínio)

Contém a lógica de negócio pura, independente de frameworks:

- Entidades ricas com comportamento
- Value Objects que garantem consistência
- Repositórios como interfaces (contrato)
- Exceções de domínio específicas

Exemplo de entidade:
```java
public class OrdemServico {
  private Long id;
  private Cliente cliente;
  private Veiculo veiculo;
  private StatusOrdem status;
  private List<Servico> servicos;
  private List<Peca> pecas;
  private BigDecimal valorTotal;
  
  // Lógica de negócio
  public void aprovar(Usuario usuario) {
    if (status != StatusOrdem.AGUARDANDO_APROVACAO) {
      throw new IllegalStateException("Ordem não pode ser aprovada");
    }
    this.status = StatusOrdem.EM_EXECUCAO;
  }
}
```

#### Application (Aplicação)

Orquestra o fluxo de negócio usando serviços do domínio:

- Casos de uso implementados como serviços
- DTOs para transferência entre camadas
- Transações gerenciadas em nível de aplicação

Exemplo de serviço:
```java
@Service
public class GerenciadorOrdemServico {
  
  public OrdemServicoDTO criarOrdem(CriarOrdemDTO dto) {
    // 1. Validar cliente
    // 2. Validar veículo
    // 3. Criar ordem
    // 4. Calcular orçamento
    // 5. Persistir
    // 6. Retornar DTO
  }
}
```

#### Infrastructure (Infraestrutura)

Implementações técnicas de interfaces do domínio:

- Repositórios JPA
- Configuração de banco de dados
- Provedores de segurança (JWT)
- Mapeadores de entidade para tabelas

#### API (Interface)

Expõe funcionalidades via HTTP REST:

- Controllers REST
- Serialização/desserialização JSON
- Validação de entrada
- Respostas HTTP

## Testes

### Cobertura de Testes

Configurado em `pom.xml` via JaCoCo:

- Cobertura mínima: 80% das linhas
- Pacotes monitorados: `com.oficina.tech_challenge.domain` e `com.oficina.tech_challenge.application`

### Executar Testes

```bash
# Todos os testes com coverage report
mvn test

# Apenas testes de um pacote específico
mvn test -Dtest=com/oficina/tech_challenge/domain/**

# Ver relatório de coverage
open target/site/jacoco/index.html
```

### Tipos de Testes

#### Testes Unitários

Testam unidades isoladas de código:

```java
@Test
void deveCriarOrdemComClienteEVeiculo() {
  Cliente cliente = new Cliente("123.456.789-00", "João Silva");
  Veiculo veiculo = new Veiculo("ABC-1234", "Fiat", "Uno", 2020);
  
  OrdemServico ordem = new OrdemServico(cliente, veiculo);
  
  assertNotNull(ordem.getId());
  assertEquals(StatusOrdem.RECEBIDA, ordem.getStatus());
}
```

#### Testes de Integração

Testam fluxo completo incluindo banco de dados:

```java
@SpringBootTest
@Transactional
class OrdemServicoIntegrationTest {
  
  @Autowired
  private GerenciadorOrdemServico gerenciador;
  
  @Test
  void devePersistirOrdemNoDatabase() {
    OrdemServicoDTO dto = criarOrdemDTO();
    OrdemServicoDTO resultado = gerenciador.criar(dto);
    
    assertNotNull(resultado.getId());
  }
}
```

## Configuração do Banco de Dados

### PostgreSQL

Configurações padrão (sobrescritáveis via variáveis de ambiente):

```properties
spring.datasource.url=jdbc:postgresql://db:5432/oficina
spring.datasource.username=user
spring.datasource.password=password
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

### Hibernate

Schema management:

```properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
```

## Segurança e Qualidade

### Boas Práticas Implementadas

#### OWASP Top 10

- A01:2021 Broken Access Control: Autenticação JWT obrigatória
- A02:2021 Cryptographic Failures: Senhas hasheadas, tokens criptografados
- A03:2021 Injection: Queries parametrizadas via JPA
- A07:2021 Cross-Site Scripting (XSS): JSON responses, sem HTML rendering

#### Clean Code

- Nomenclatura clara e significativa
- Métodos pequenos e focused
- Responsabilidade única (SRP)
- Testabilidade garantida

#### DDD

- Ubiquitous Language: Termos do domínio consistentes
- Bounded Contexts: Separação clara de contextos
- Entidades com identidade
- Value Objects imutáveis

### Justificativa do PostgreSQL

PostgreSQL foi escolhido como SGBD pela seguinte análise:

#### Vantagens

1. ACID Completo: Garante transações seguras em operações de orçamento e estoque
2. Integridade Referencial: Foreign keys garantem consistência de dados
3. Tipos Avançados: JSON, UUID, Arrays nativamente suportados
4. Performance: Índices B-tree otimizados para queries frequentes
5. Open Source: Sem custos de licença, comunidade ativa
6. Docker: Imagem oficial estável e otimizada

#### Alternativas Descartadas

- MySQL: Menos robusto em transações complexas
- MongoDB: Sem garantias de integridade referencial (necessária para estoque)
- SQLite: Inadequado para aplicação multi-usuário

## Configurações de Ambiente

### Variáveis de Ambiente

```bash
# Banco de Dados
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/oficina
SPRING_DATASOURCE_USERNAME=user
SPRING_DATASOURCE_PASSWORD=password

# Segurança
SECURITY_JWT_SECRET=sua_chave_secreta_muito_longa_e_forte_aqui
SECURITY_JWT_EXPIRATION=3600000

# Aplicação
SERVER_PORT=8080
SPRING_JPA_HIBERNATE_DDL_AUTO=update
```

### Profiles Spring

Suporte para múltiplos ambientes:

```bash
# Development
mvn spring-boot:run -Dspring.profiles.active=dev

# Production
java -jar app.jar --spring.profiles.active=prod
```

## Troubleshooting

### Erro: "Connection refused" ao banco de dados

Verificar:
1. Docker Compose está executando: `docker compose ps`
2. Banco de dados está saudável: `docker compose logs db`
3. Porta 5432 não está bloqueada: `lsof -i :5432`

### Erro: "Port 8080 already in use"

Solução:
```bash
# Encontrar processo na porta
lsof -i :8080

# Matar processo ou usar porta diferente
docker compose down
```

### Erro: "JWTException" em requisições

Verificar:
1. Token está no header: `Authorization: Bearer <token>`
2. Token não expirou
3. Chave secreta está correta no servidor

## Endpoints de Monitoramento

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

Resposta esperada:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    }
  }
}
```

## Contribuição e Desenvolvimento

### Padrão de Commits

Manter histórico limpo e compreensível:

```
git commit -m "feat: adicionar cálculo de orçamento automático"
git commit -m "fix: corrigir validação de CPF inválido"
git commit -m "test: adicionar testes de integração para ordem de serviço"
```

### Branches

- `main`: Produção
- `develop`: Desenvolvimento
- `feature/*`: Novas funcionalidades
- `hotfix/*`: Correções urgentes

## Referências

- Spring Boot Documentation: https://spring.io/projects/spring-boot
- Domain-Driven Design: Eric Evans - "Domain-Driven Design: Tackling Complexity in the Heart of Software"
- OWASP Top 10: https://owasp.org/www-project-top-ten/
- PostgreSQL Documentation: https://www.postgresql.org/docs/
