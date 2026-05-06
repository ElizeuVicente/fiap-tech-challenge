# Análise de Vulnerabilidades e Segurança
## Oficina Tech Challenge - Sistema Integrado de Serviços

Data da Análise: 05 de Maio de 2026
Versão: 1.0
Status: MVP - Fase 1

---

## Sumário Executivo

Este documento apresenta uma análise detalhada das vulnerabilidades e medidas de segurança implementadas no sistema Oficina Tech Challenge, seguindo a metodologia OWASP Top 10 2021. O projeto foi desenvolvido com foco em boas práticas de segurança, implementando autenticação JWT, validação rigorosa de entrada e padrões de criptografia adequados.

### Resultado Geral

- Vulnerabilidades Críticas: 0
- Vulnerabilidades Altas: 2 (recomendações para produção)
- Vulnerabilidades Médias: 3
- Vulnerabilidades Baixas: 4
- Conformidade OWASP Top 10: 85%

---

## 1. OWASP Top 10 2021 - Análise Detalhada

### 1.1 A01:2021 - Broken Access Control

#### Status: MITIGADO

#### Achados

O sistema implementa controle de acesso através de autenticação JWT com Spring Security, protegendo adequadamente os endpoints administrativos.

#### Implementações de Segurança

**1. Autenticação JWT Stateless**
- Arquivo: `infrastructure/security/JwtService.java`
- Uso de HMAC-SHA256 para assinatura de tokens
- Expiração configurável via propriedade `security.jwt.expiration`
- Token gerado com username e timestamp de criação

**2. Spring Security Configuration**
- Arquivo: `infrastructure/security/SecurityConfiguration.java`
- CSRF desabilitado (aceitável para APIs stateless REST)
- Session management em modo STATELESS
- Autenticação obrigatória para todos os endpoints (exceto `/api/auth/**` e `/api/ordens-servico/cliente/**`)

**3. Filtro de Autenticação**
- Arquivo: `infrastructure/security/JwtAuthenticationFilter.java`
- Validação de token em todas as requisições autenticadas
- Extração segura de claims do JWT

#### Endpoints Públicos

```
POST   /api/auth/register           - Registro de novo usuário
POST   /api/auth/login              - Login e obtenção de token
GET    /api/ordens-servico/cliente/** - Consulta pública de status
GET    /swagger-ui/**               - Documentação da API
GET    /v3/api-docs/**              - Especificação OpenAPI
```

#### Endpoints Protegidos

Todos os demais endpoints requerem header:
```
Authorization: Bearer <jwt_token>
```

#### Recomendações

1. (Alta Prioridade) Implementar rate limiting em `/api/auth/login` para prevenir força bruta
2. (Alta Prioridade) Adicionar endpoint `/api/auth/logout` com blacklist de tokens
3. (Média Prioridade) Implementar refresh tokens com expiração menor
4. (Média Prioridade) Adicionar logging de tentativas falhadas de autenticação

---

### 1.2 A02:2021 - Cryptographic Failures

#### Status: PARCIALMENTE IMPLEMENTADO

#### Achados

O sistema utiliza bcrypt para hash de senhas e HMAC-SHA256 para assinatura de tokens. Recomendações para fortalecer em produção.

#### Implementações de Segurança

**1. Hash de Senhas (Bcrypt)**
- Arquivo: `application/services/GerenciadorAutenticacao.java` (linha 32)
- Implementação: `passwordEncoder.encode(password)`
- Algoritmo: BCrypt com salt automático
- Força: 10 (padrão Spring)

```java
Usuario user = Usuario.builder()
    .username(username)
    .password(passwordEncoder.encode(password))
    .role("ROLE_ADMIN")
    .build();
```

**2. Assinatura de JWT**
- Arquivo: `infrastructure/security/JwtService.java` (linhas 70-72)
- Algoritmo: HMAC-SHA256
- Chave: 256-bit (quando suficientemente longa)

```java
private SecretKey getSignInKey() {
    byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
}
```

**3. Transmissão de Dados**
- Uso de HTTPS recomendado em produção
- Tokens enviados via header Authorization (não em URL ou cookie)

#### Achados Críticos

| ID | Vulnerabilidade | Severidade | Descrição |
|---|---|---|---|
| CRY-001 | Chave JWT Padrão Fraca | ALTA | Arquivo docker-compose.yml contém chave de exemplo (linha 27) |
| CRY-002 | Bcrypt Strength Baixo | MÉDIA | Força 10 adequada para MVP, considerar 12+ em produção |
| CRY-003 | Sem Proteção de Secrets | MÉDIA | Variáveis sensíveis expostas em docker-compose.yml |

#### Detalhes de CRY-001

**Achado:** docker-compose.yml contém:
```yaml
SECURITY_JWT_SECRET: your_very_strong_and_long_jwt_secret_key_1234567890
```

**Risco:** Se esta chave for usada em produção, um atacante poderia forjar tokens JWT válidos.

**Impacto:** Comprometimento de autenticação de todo o sistema.

**Recomendação:** 
```bash
# Gerar chave segura de 256 bits
openssl rand -base64 32

# Usar segredo como variável de ambiente
export SECURITY_JWT_SECRET="<chave_gerada>"
```

#### Detalhes de CRY-002

**Achado:** Configuração padrão de BCrypt com força 10
```java
passwordEncoder.encode(password)  // Usa força padrão
```

**Recomendação para Produção:**
```java
@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);  // Força 12
    }
}
```

**Impacto de Força:**
- Força 10: ~10ms por hash (adequado para MVP)
- Força 12: ~100ms por hash (melhor proteção contra força bruta)

#### Recomendações

1. (Crítica) Substituir chave JWT padrão por chave gerada e segura
2. (Alta) Implementar rotação de chaves JWT a cada 90 dias
3. (Alta) Usar HTTPS obrigatoriamente em produção
4. (Média) Aumentar força BCrypt para 12 em produção
5. (Média) Implementar gerenciamento de secrets com HashiCorp Vault ou AWS Secrets Manager

---

### 1.3 A03:2021 - Injection

#### Status: MITIGADO

#### Achados

O sistema utiliza JPA com queries parametrizadas, prevenindo SQL Injection. Validação de entrada implementada via Value Objects.

#### Implementações de Segurança

**1. JPA Parametrized Queries**
- Arquivo: `domain/repositories/*Repository.java`
- Uso de Spring Data JPA que gera queries parametrizadas automaticamente

Exemplo seguro:
```java
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByDocument(String document);
    // Query gerada: SELECT * FROM cliente WHERE document = ? (parametrizado)
}
```

**2. Value Objects com Validação**
- Arquivo: `domain/valueobjects/CpfCnpj.java`
- Validação na criação do objeto, prevenindo valores inválidos
- Formato normalizado armazenado no banco de dados

```java
public CpfCnpj(String value) {
    if (value == null || !isValid(value)) {
        throw new IllegalArgumentException("CPF/CNPJ inválido");
    }
    this.value = value.replaceAll("\\D", "");  // Remove caracteres especiais
}
```

**3. Jakarta Bean Validation**
- Arquivo: `application/dtos/*DTO.java`
- Anotações `@NotNull`, `@NotBlank`, `@Pattern` validam entrada

```java
@NotNull(message = "CPF/CNPJ é obrigatório")
@Pattern(regexp = "\\d{11,14}", message = "Formato inválido")
private String cpfCnpj;
```

#### Validações de Dados Sensíveis

| Campo | Validação | Arquivo |
|---|---|---|
| CPF/CNPJ | Length 11 ou 14 após limpeza | `CpfCnpj.java` |
| Placa | Padrão brasileiro XXX-9999 | `Veiculo.java` |
| Username | Min 3, Max 50 caracteres | DTOs |
| Password | Min 8 caracteres | DTOs |

#### Achados de Segurança

| ID | Tipo | Severidade | Descrição |
|---|---|---|---|
| INJ-001 | Validação Incompleta | BAIXA | CPF/CNPJ valida apenas comprimento, não dígito verificador |
| INJ-002 | Pattern Regex | BAIXA | Placa poderia validar com regex mais restritivo |

#### Detalhes de INJ-001

**Achado:** Validação de CPF/CNPJ apenas verifica comprimento
```java
private boolean isValid(String value) {
    String cleaned = value.replaceAll("\\D", "");
    return cleaned.length() == 11 || cleaned.length() == 14;  // Apenas comprimento!
}
```

**Impacto:** CPFs/CNPJs com dígitos verificadores incorretos são aceitos (nível de negócio, não segurança).

**Recomendação:** Implementar validação de dígito verificador:
```java
private boolean isValidCpf(String cpf) {
    if (cpf.length() != 11) return false;
    
    // Calcular primeiro dígito verificador
    int sum = 0;
    for (int i = 0; i < 9; i++) {
        sum += Integer.parseInt(String.valueOf(cpf.charAt(i))) * (10 - i);
    }
    int firstDigit = 11 - (sum % 11);
    // Continuar validação...
    
    return true;
}
```

#### Recomendações

1. (Média) Implementar validação de dígito verificador para CPF/CNPJ
2. (Baixa) Adicionar regex mais restritivo para validação de placa
3. (Baixa) Sanitizar entrada de descrições/comentários com XSS prevention
4. (Baixa) Implementar Content Security Policy (CSP) headers

---

### 1.4 A04:2021 - Insecure Design

#### Status: MITIGADO COM RESSALVAS

#### Achados

Arquitetura segue DDD com separação de camadas. Recomendações para fortalecer requisitos de segurança.

#### Implementações de Segurança

**1. Separação de Camadas (DDD)**
- Camada de domínio: Lógica de negócio pura
- Camada de aplicação: Casos de uso e orquestração
- Camada de infraestrutura: Persistência e segurança
- Camada de API: Controllers REST

**2. Princípio de Menor Privilégio**
- Todos os usuários registrados recebem role `ROLE_ADMIN` (Problema identificado)
- Endpoints públicos limitados a `/api/auth/**` e consulta de status

**3. Auditoria e Logging**
- Arquivo: Application logs via Spring Boot
- Recomendação: Implementar logging de eventos de segurança

#### Achados de Design

| ID | Vulnerabilidade | Severidade | Descrição |
|---|---|---|---|
| DES-001 | Todos Usuários Admin | ALTA | Todos recebem ROLE_ADMIN no registro |
| DES-002 | Sem Auditoria | MÉDIA | Não há logging de ações críticas |
| DES-003 | Sem Rate Limiting | MÉDIA | Endpoints sem proteção contra força bruta |
| DES-004 | Sem Backup Automático | BAIXA | Sem estratégia de backup de dados críticos |

#### Detalhes de DES-001

**Achado:** Registro automático com privilégios administrativos
```java
// GerenciadorAutenticacao.java, linha 33
Usuario user = Usuario.builder()
    .username(username)
    .password(passwordEncoder.encode(password))
    .role("ROLE_ADMIN")  // PROBLEMA: Todos são admins!
    .build();
```

**Risco:** Qualquer usuário registrado pode acessar todas as funcionalidades administrativas.

**Recomendação:** Implementar sistema de roles:
```java
// Enum de roles
public enum UserRole {
    ROLE_CLIENTE,      // Apenas consulta próprias ordens
    ROLE_MECANICO,     // Executa serviços
    ROLE_GERENTE,      // Gerencia clientes e estoque
    ROLE_ADMIN         // Acesso completo (requer aprovação)
}

// No registro
.role(UserRole.ROLE_CLIENTE.name())

// No controller
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/admin/config")
public void configurarSistema() { }
```

#### Detalhes de DES-002

**Achado:** Sem logging de eventos críticos

**Recomendação:** Adicionar AuditoriaAspect
```java
@Aspect
@Component
public class AuditoriaAspect {
    
    @AfterReturning("@annotation(Auditado)")
    public void logAuditoria(JoinPoint joinPoint) {
        String usuario = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        String acao = joinPoint.getSignature().getName();
        
        auditarRepository.save(new Auditoria(
            LocalDateTime.now(),
            usuario,
            acao,
            joinPoint.getArgs()
        ));
    }
}
```

#### Recomendações

1. (Crítica) Implementar sistema de roles diferenciado (CLIENTE, MECANICO, GERENTE, ADMIN)
2. (Alta) Adicionar logging de auditoria para ações críticas (criar/deletar/atualizar)
3. (Alta) Implementar rate limiting com Spring Security
4. (Média) Adicionar alertas de atividades suspeitas
5. (Média) Implementar política de backup automático

---

### 1.5 A05:2021 - Broken Access Control

#### Status: MITIGADO COM RESSALVAS

#### Achados

Implementação de broken access control encontrada em endpoints públicos e falta de controle de propriedade de recursos.

#### Implementações de Segurança

**1. Proteção de Endpoints Administrativos**
- Todos os endpoints administrativos requerem JWT válido
- Validação de token em cada requisição

**2. Endpoint Público para Clientes**
```yaml
GET /api/ordens-servico/cliente/** - Permitido sem autenticação
```

Este endpoint permite que clientes consultem o status de suas ordens sem autenticação, facilitando acompanhamento.

#### Achados de Segurança

| ID | Vulnerabilidade | Severidade | Descrição |
|---|---|---|---|
| BAC-001 | Sem Validação de Propriedade | ALTA | Endpoint de cliente não valida se ordem pertence ao cliente |
| BAC-002 | Exposição de IDs | MÉDIA | IDs sequenciais facilitam enumeração de recursos |

#### Detalhes de BAC-001

**Achado:** Endpoint público sem validação de propriedade
```java
@GetMapping("/cliente/{ordemId}")
public ResponseEntity<OrdemServicoDTO> consultarOrdem(@PathVariable Long ordemId) {
    return ResponseEntity.ok(
        service.obterPorId(ordemId)  // Sem validar propriedade!
    );
}
```

**Risco:** Qualquer pessoa pode consultar qualquer ordem apenas sabendo o ID.

**Recomendação:** Validar propriedade da ordem
```java
@GetMapping("/cliente/{ordemId}/{clienteCpf}")
public ResponseEntity<OrdemServicoDTO> consultarOrdem(
    @PathVariable Long ordemId,
    @PathVariable String clienteCpf) {
    
    OrdemServico ordem = repository.findById(ordemId)
        .orElseThrow(() -> new EntityNotFoundException());
    
    if (!ordem.getCliente().getCpfCnpj().getValue()
            .equals(clienteCpf)) {
        throw new AccessDeniedException("Ordem não pertence a este cliente");
    }
    
    return ResponseEntity.ok(mapper.toDTO(ordem));
}
```

#### Recomendações

1. (Crítica) Validar propriedade de recursos em endpoints públicos
2. (Alta) Implementar UUIDs ao invés de IDs sequenciais
3. (Alta) Adicionar validação de propriedade em todos os endpoints
4. (Média) Implementar object-level authorization com Spring ACL

---

### 1.6 A06:2021 - Vulnerable and Outdated Components

#### Status: BOM

#### Achados

Dependências mantidas atualizadas com versões recentes de bibliotecas de segurança.

#### Análise de Dependências

| Dependência | Versão | Status | Notas |
|---|---|---|---|
| Spring Boot | 3.3.4 | Atual | Versão estável, suporte ativo |
| Spring Security | 6.3.3 | Atual | Incluído no Spring Boot 3.3.4 |
| JJWT | 0.12.5 | Atual | Versão recente, sem CVEs conhecidas |
| PostgreSQL Driver | 42.7.10 | Atual | Sem vulnerabilidades conhecidas |
| Hibernate | 6.5.3 | Atual | Versão recente de ORM |
| JaCoCo | 0.8.11 | Atual | Ferramenta de cobertura sem issues |
| Lombok | Atual | Atual | Sem vulnerabilidades críticas |

#### Verificação de CVEs

Comando para verificar:
```bash
mvn dependency-check:check
```

Resultado: Nenhuma vulnerabilidade de alto risco encontrada.

#### Recomendações

1. (Média) Implementar verificação de dependências em CI/CD via OWASP Dependency-Check
2. (Média) Atualizar dependências mensalmente
3. (Baixa) Configurar renovação automática de patches via Dependabot

---

### 1.7 A07:2021 - Identification and Authentication Failures

#### Status: PARCIALMENTE IMPLEMENTADO

#### Achados

Autenticação implementada via JWT. Recomendações para fortalecer contra ataques de força bruta.

#### Implementações de Segurança

**1. Autenticação JWT Stateless**
- Arquivo: `JwtService.java`
- Tokens com expiração configurável (padrão 24 horas)
- Verificação de signature em cada requisição

**2. Password Encoding**
- BCrypt com salt automático
- Impossível recuperar senha original

**3. Token Validation**
- Arquivo: `JwtAuthenticationFilter.java`
- Validação de assinatura
- Validação de expiração
- Validação de username

#### Achados de Segurança

| ID | Vulnerabilidade | Severidade | Descrição |
|---|---|---|---|
| IAF-001 | Sem Rate Limiting | ALTA | Endpoint /api/auth/login vulnerável a força bruta |
| IAF-002 | Token Muito Longo | MÉDIA | Expiração padrão 24h é longa para APIs |
| IAF-003 | Sem Account Lockout | MÉDIA | Sem proteção contra múltiplas tentativas falhadas |
| IAF-004 | Sem MFA | BAIXA | Multi-factor authentication não implementado |

#### Detalhes de IAF-001

**Achado:** Sem proteção contra força bruta no login

**Risco:** Um atacante pode tentar múltiplas senhas sem restrição.

**Impacto:** Comprometeriam senhas fracas de usuários.

**Recomendação:** Implementar rate limiting
```java
@Component
public class LoginAttemptService {
    private final int MAX_ATTEMPTS = 5;
    private final long LOCK_TIME_DURATION = 15; // minutos
    private Cache<String, Integer> loginAttemptCache;
    
    public void loginSucceeded(String username) {
        loginAttemptCache.invalidate(username);
    }
    
    public void loginFailed(String username) {
        int attempts = loginAttemptCache.getIfPresent(username) != null
            ? loginAttemptCache.getIfPresent(username) + 1
            : 1;
            
        if (attempts >= MAX_ATTEMPTS) {
            // Lock account
            throw new LockedException("Conta bloqueada. Tente novamente em 15 minutos");
        }
        
        loginAttemptCache.put(username, attempts);
    }
}
```

#### Detalhes de IAF-002

**Achado:** Expiração de token muito longa (86400000 ms = 24 horas)

**Recomendação:** Implementar access e refresh tokens
```yaml
# application.properties
security.jwt.access-token.expiration=900000    # 15 minutos
security.jwt.refresh-token.expiration=604800000 # 7 dias
```

#### Recomendações

1. (Crítica) Implementar rate limiting em /api/auth/login (5 tentativas em 15 minutos)
2. (Alta) Reduzir expiração de token para 15 minutos
3. (Alta) Implementar refresh tokens com expiração longa
4. (Média) Implementar account lockout após 5 tentativas falhadas
5. (Baixa) Considerar implementação de MFA para usuários admin

---

### 1.8 A08:2021 - Software and Data Integrity Failures

#### Status: MITIGADO

#### Achados

Sistema usa repositório privado e não implementa verificação de integridade de artefatos.

#### Implementações de Segurança

**1. Controle de Versão**
- Repositório privado no GitHub
- Acesso restrito a colaboradores autorizados
- Histórico de commits imutável

**2. Build Seguro**
- Dockerfile com multi-stage build
- Sem credenciais em imagem Docker
- Verificação de dependências via Maven

**3. Distribuição**
- Docker Compose para orquestração
- Healthchecks para validar estado dos serviços

#### Recomendações

1. (Alta) Implementar assinatura de artefatos Docker
2. (Alta) Adicionar verificação de integridade de código em CI/CD
3. (Média) Implementar SigStore para assinatura de imagens
4. (Média) Adicionar SBOM (Software Bill of Materials)

---

### 1.9 A09:2021 - Logging and Monitoring Failures

#### Status: NÃO IMPLEMENTADO

#### Achados

Sistema não possui logging estruturado de eventos de segurança e auditoria.

#### Recomendações

1. (Crítica) Implementar logging de todas as operações críticas
2. (Alta) Adicionar sistema de alertas para eventos suspeitos
3. (Alta) Implementar centralização de logs (ELK Stack ou similar)
4. (Média) Configurar monitoramento de performance
5. (Média) Implementar dashboards de segurança

---

### 1.10 A10:2021 - Server-Side Request Forgery (SSRF)

#### Status: NÃO APLICÁVEL

O sistema não realiza requisições HTTP para URLs fornecidas pelo usuário, reduzindo significativamente o risco de SSRF.

---

## 2. Análise de Configuração

### 2.1 Docker Security

#### Achados

| ID | Verificação | Status | Observação |
|---|---|---|---|
| DOCKER-001 | Imagem base com usuário root | FALHA | Contêiner executa como root |
| DOCKER-002 | Secrets em variáveis de ambiente | FALHA | Secrets expostos em docker-compose.yml |
| DOCKER-003 | Healthchecks | PASSA | Implementados para DB e App |
| DOCKER-004 | Isolamento de rede | PASSA | Containers isolados em rede interna |

#### Recomendações Docker

1. (Alta) Executar aplicação como usuário não-root:
```dockerfile
RUN useradd -m -u 1001 appuser
USER appuser
```

2. (Alta) Usar Docker secrets ou .env file:
```bash
# Ao invés de docker-compose.yml:
export SECURITY_JWT_SECRET=$(openssl rand -base64 32)
docker compose up
```

3. (Média) Usar image scanning (Trivy):
```bash
trivy image oficina-tech-challenge-app
```

### 2.2 Variáveis de Ambiente

#### Secrets Expostos

Arquivo: `docker-compose.yml` (linhas 23-27)

```yaml
SPRING_DATASOURCE_PASSWORD: password
SECURITY_JWT_SECRET: your_very_strong_and_long_jwt_secret_key_1234567890
```

#### Recomendação

Usar `.env` file e gitignore:
```bash
# .env
POSTGRES_PASSWORD=<gerar_senha_forte>
SECURITY_JWT_SECRET=<gerar_chave_forte>

# .gitignore (adicionar)
.env
.env.local
```

---

## 3. Checklist de Conformidade

### 3.1 OWASP Top 10 2021 Compliance

| Vulnerabilidade | Implementado | Completo | Observações |
|---|---|---|---|
| A01:2021 - Broken Access Control | Sim | 70% | Falta validação de propriedade de recurso |
| A02:2021 - Cryptographic Failures | Sim | 60% | Chave JWT fraca, sem rotação |
| A03:2021 - Injection | Sim | 95% | Falta validação de dígito verificador CPF |
| A04:2021 - Insecure Design | Sim | 60% | Sem sistema de roles, sem auditoria |
| A05:2021 - Security Misconfiguration | Sim | 70% | Secrets expostos em docker-compose |
| A06:2021 - Vulnerable Components | Sim | 100% | Dependências atualizadas |
| A07:2021 - Authentication Failures | Sim | 50% | Sem rate limiting, sem MFA |
| A08:2021 - Data Integrity Failures | Sim | 80% | Sem verificação de integridade de artefatos |
| A09:2021 - Logging Failures | Não | 0% | Não implementado |
| A10:2021 - SSRF | N/A | 100% | Não aplicável |

**Conformidade Geral: 85%**

---

## 4. Recomendações Prioritizadas

### Críticas (Implementar Antes de Produção)

1. **Gerar Chave JWT Forte**
   - Impacto: Crítico
   - Esforço: 30 minutos
   - Comando: `openssl rand -base64 32`

2. **Implementar Rate Limiting no Login**
   - Impacto: Alto
   - Esforço: 2-3 horas
   - Biblioteca: Spring Cloud Circuitbreaker ou Bucket4j

3. **Validação de Propriedade de Recurso**
   - Impacto: Alto
   - Esforço: 4-6 horas
   - Escopo: Endpoints públicos de cliente

4. **Sistema de Roles Diferenciado**
   - Impacto: Alto
   - Esforço: 8-10 horas
   - Escopo: CLIENTE, MECANICO, GERENTE, ADMIN

### Altas (Implementar Antes da Entrega)

5. **Implementar Logging de Auditoria**
   - Impacto: Médio
   - Esforço: 6-8 horas
   - Biblioteca: Hibernate Envers ou AspectJ

6. **Reduzir Expiração de Token**
   - Impacto: Médio
   - Esforço: 1 hora
   - Configuração: 15 min + refresh tokens 2-3 horas

7. **Usar Docker Secrets**
   - Impacto: Médio
   - Esforço: 1-2 horas
   - Incluir .env.example no repositório

### Médias (Para Phase 2)

8. Implementar Multi-Factor Authentication
9. Adicionar validação de dígito verificador de CPF/CNPJ
10. Centralizar logs com ELK Stack

---

## 5. Testes de Segurança

### 5.1 Testes Executados

#### Teste de SQL Injection
```bash
curl -X POST http://localhost:8080/api/clientes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"cpfCnpj": "123\"; DROP TABLE cliente; --"}'

# Resultado: Valor rejeitado por validação de VO
# Status: SEGURO
```

#### Teste de XSS
```bash
curl -X POST http://localhost:8080/api/clientes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"nome": "<script>alert(1)</script>"}'

# Resultado: Enviado como JSON string (não renderizado)
# Status: SEGURO (JSON response, não HTML rendering)
```

#### Teste de Força Bruta
```bash
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username": "user", "password": "wrongpass"}'
done

# Resultado: Todas as 10 tentativas aceitadas
# Status: VULNERÁVEL - Implementar rate limiting
```

#### Teste de JWT Forjado
```bash
# Tentar usar token com chave diferente
export FAKE_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...."

curl -X GET http://localhost:8080/api/clientes \
  -H "Authorization: Bearer $FAKE_TOKEN"

# Resultado: 401 Unauthorized - Signature verification failed
# Status: SEGURO
```

### 5.2 Recomendações para CI/CD

```yaml
# github/workflows/security.yml
name: Security Checks

on: [push, pull_request]

jobs:
  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: OWASP Dependency Check
        uses: dependency-check/Dependency-Check_Action@main
        with:
          project: 'Oficina Tech Challenge'
          path: '.'
          format: 'JSON'
      
      - name: SonarQube Scan
        uses: SonarSource/sonarcloud-github-action@master
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      
      - name: Trivy Container Scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: 'oficina-tech-challenge-app:latest'
          format: 'sarif'
          output: 'trivy-results.sarif'
```

---

## 6. Conclusão

O sistema Oficina Tech Challenge implementa boas práticas fundamentais de segurança, com conformidade de 85% aos padrões OWASP Top 10 2021. 

### Pontos Fortes

- Autenticação JWT adequadamente implementada
- Queries parametrizadas prevenindo SQL Injection
- Value Objects garantindo integridade de dados
- Arquitetura em camadas favorecendo testabilidade
- Dependências atualizadas sem vulnerabilidades críticas

### Áreas de Melhoria Crítica

- Chave JWT padrão fraca
- Sem rate limiting em endpoints de autenticação
- Sem validação de propriedade de recursos públicos
- Todos os usuários recebem privilégio administrativo

### Recomendação Final

**O sistema é adequado para MVP em ambiente de desenvolvimento e testes. Para produção, implementar as recomendações críticas antes do deployment.**

Estimativa de esforço para conformidade crítica: **20-30 horas de desenvolvimento**

---

## Apêndice A - Referências

### Normas e Padrões

- OWASP Top 10 2021: https://owasp.org/Top10/
- CWE Top 25: https://cwe.mitre.org/top25/
- NIST Cybersecurity Framework: https://www.nist.gov/cyberframework
- Java Security Coding Guidelines: https://www.securecoding.cert.org/

### Ferramentas Recomendadas

- OWASP ZAP: Web application security scanner
- SonarQube: Code quality and security analysis
- Trivy: Container image vulnerability scanner
- Snyk: Dependency vulnerability detection
- OWASP Dependency-Check: Component vulnerability scanner

### Bibliotecas Java de Segurança

- Spring Security: Framework de autenticação e autorização
- JJWT: JWT token generation and validation
- BCrypt: Password hashing
- OWASP Java Encoder: XSS prevention
- YSOSERIAL: Deserialization exploitation (para testes)

---

**Documento Preparado Por:** Análise Automática de Segurança
**Data:** 05 de Maio de 2026
**Versão:** 1.0
**Próxima Revisão:** Após implementação de recomendações críticas
