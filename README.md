# Bank API

REST API de operações bancárias (contas e transações) construída em **Java 21 + Spring Boot 3**.
Projeto de portfólio com foco em backend bancário: regras de negócio financeiras, testes automatizados, documentação OpenAPI e containerização.

> Em construção — evoluído com commits diários. Acompanhe o [roadmap](#roadmap).

## Stack

- Java 21, Spring Boot 3.3
- Spring Web, Spring Data JPA, Bean Validation
- H2 (dev/teste) e PostgreSQL (profile `postgres`), com schema versionado por Flyway
- springdoc-openapi (Swagger UI)
- JUnit 5, MockMvc
- Docker (multi-stage), GitHub Actions (CI)

## Como rodar

```bash
# testes
mvn clean verify

# subir a aplicação (porta 8080)
mvn spring-boot:run

# ou via Docker
docker build -t bank-api .
docker run -p 8080:8080 bank-api
```

Por padrão a aplicação sobe com **H2 em memória** (schema criado pelo Hibernate),
ideal para rodar e testar sem dependências externas.

Para usar **PostgreSQL** com migrações **Flyway** (mais próximo de produção):

```bash
# sobe um Postgres local
docker compose up -d

# roda a API no profile postgres (Flyway aplica db/migration/*.sql no boot)
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 console (apenas no profile padrão): http://localhost:8080/h2-console (JDBC `jdbc:h2:mem:bankdb`, user `sa`)

## Endpoints (v1)

| Método | Rota                       | Descrição                |
|--------|----------------------------|--------------------------|
| POST   | `/api/v1/auth/register`         | Cadastra usuário e devolve JWT |
| POST   | `/api/v1/auth/login`            | Autentica e devolve JWT       |
| POST   | `/api/v1/accounts`              | Cria conta (saldo zero) 🔒    |
| GET    | `/api/v1/accounts/{id}`         | Consulta conta por id         |
| POST   | `/api/v1/accounts/{id}/deposit` | Deposita valor na conta       |
| POST   | `/api/v1/accounts/{id}/withdraw`| Saca valor (valida saldo)     |
| POST   | `/api/v1/accounts/{id}/transfer`| Transfere para outra conta (atômico) |
| GET    | `/api/v1/accounts/{id}/statement`| Extrato da conta paginado (`?page=&size=`) 🔒|
| GET    | `/api/v1/accounts/health`       | Health check                  |
| GET    | `/actuator/health`              | Health do app (probes)        |
| GET    | `/actuator/info`                | Metadados do app              |
| GET    | `/actuator/metrics`             | Métricas (JVM, HTTP, negócio) 🔒|

> 🔒 = exige `Authorization: Bearer <token>`. As rotas de conta são protegidas; obtenha um token em `/api/v1/auth/register` ou `/api/v1/auth/login`.

Exemplo (registrar, pegar o token e criar conta autenticado):

```bash
# 1) cadastrar e capturar o token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"lucas","password":"supersecret1"}' | jq -r .token)

# 2) usar o token nas operações de conta
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Lucas Andrade","document":"12345678901"}'
```

## Decisões de design

- **Saldo em `BigDecimal`** (nunca `double`) — evita erro de arredondamento em dinheiro.
- **DTOs (records) separados da entidade** — entidade JPA não vaza pela API.
- **Tratamento de erro centralizado** (`@RestControllerAdvice`) com corpo de erro padronizado.
- **Arquitetura em camadas** controller → service → repository.
- **Autenticação JWT stateless** (Spring Security) — senha guardada como hash BCrypt, segredo do token via configuração/env, filtro `OncePerRequestFilter` valida o `Bearer` em cada requisição.
- **Schema versionado por Flyway** no profile `postgres` (`ddl-auto: validate`) — em produção o banco é dono do schema e o Hibernate apenas valida que as entidades batem; H2 com `ddl-auto: update` segue para dev/teste rápidos.
- **Extrato paginado** — o extrato nunca devolve todos os lançamentos de uma vez (uma conta pode ter milhares); o cliente pede `page`/`size` e recebe um envelope `PageResponse` (`content` + `totalElements`/`totalPages`/`last`). O `size` é limitado (1–100) para proteger banco e payload, e a ordenação fica fixa no servidor (mais recente primeiro) — o cliente não escolhe a ordem.
- **Observabilidade com Actuator + Micrometer** — `/actuator/health` e `/actuator/info` ficam públicos (úteis para probes de orquestrador/load balancer); `/actuator/metrics` exige token. Além das métricas técnicas (JVM, HTTP), há uma métrica de negócio `bank.account.operations` (counter com tag `type` = `deposit`/`withdrawal`/`transfer`) que conta operações concluídas.
- **Travamento otimista no saldo (`@Version`)** — a conta tem uma coluna `version` que o JPA inclui em cada `UPDATE` (`... WHERE version = ?`) e incrementa. Se duas operações concorrentes leem o mesmo saldo e tentam gravá-lo, a segunda atualiza zero linhas e falha — evita o *lost update* (uma operação sobrescrever silenciosamente o saldo da outra). A perdedora recebe **409 Conflict** (mesmo corpo de erro padrão) e pode simplesmente repetir a requisição com o saldo já atualizado. Sem bloqueio pessimista no banco: mais escalável para o caso comum de baixa contenção.

## Roadmap

- [x] Domínio de contas (criar/consultar) + validação + testes
- [x] Depósito e saque com regras de saldo
- [x] Transferência entre contas (transacional, atômica)
- [x] Extrato / histórico de transações
- [x] Autenticação JWT (Spring Security)
- [x] Migração para PostgreSQL + Flyway
- [x] Observabilidade (Actuator, métricas)

## Autor

**Lucas Mendes de Andrade** — Engenheiro de Software backend (Java/Spring)
[LinkedIn](https://www.linkedin.com/in/devlucasandrade/) · [GitHub](https://github.com/lucasandrade95)
