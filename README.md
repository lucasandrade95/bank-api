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
| GET    | `/api/v1/accounts`              | Lista contas paginadas (`?page=&size=`) 🔒|
| GET    | `/api/v1/accounts/{id}`         | Consulta conta por id         |
| POST   | `/api/v1/accounts/{id}/deposit` | Deposita valor na conta       |
| POST   | `/api/v1/accounts/{id}/withdraw`| Saca valor (valida saldo)     |
| POST   | `/api/v1/accounts/{id}/transfer`| Transfere para outra conta (atômico) |
| POST   | `/api/v1/accounts/{id}/block`   | Congela a conta (bloqueia movimentação) 🔒|
| POST   | `/api/v1/accounts/{id}/unblock` | Reativa a conta 🔒|
| GET    | `/api/v1/accounts/{id}/statement`| Extrato da conta paginado e filtrável por período e tipo (`?page=&size=&from=&to=&type=`) 🔒|
| GET    | `/api/v1/accounts/health`       | Health check                  |
| GET    | `/actuator/health`              | Health do app (probes)        |
| GET    | `/actuator/info`                | Metadados do app              |
| GET    | `/actuator/metrics`             | Métricas (JVM, HTTP, negócio) 🔒|

> 🔒 = exige `Authorization: Bearer <token>`. As rotas de conta são protegidas; obtenha um token em `/api/v1/auth/register` ou `/api/v1/auth/login`.

> As operações de dinheiro (`deposit`, `withdraw`, `transfer`) aceitam um cabeçalho opcional `Idempotency-Key`: reenviar a mesma requisição com a mesma chave devolve a resposta original sem repetir a operação (útil para *retry* seguro após timeout).

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
- **Extrato paginado e filtrável por período** — o extrato nunca devolve todos os lançamentos de uma vez (uma conta pode ter milhares); o cliente pede `page`/`size` e recebe um envelope `PageResponse` (`content` + `totalElements`/`totalPages`/`last`). O `size` é limitado (1–100) para proteger banco e payload, e a ordenação fica fixa no servidor (mais recente primeiro) — o cliente não escolhe a ordem. As datas opcionais `from`/`to` (formato ISO `YYYY-MM-DD`, ambas inclusivas) restringem o extrato a uma janela — ex.: "extrato de janeiro" com `from=2026-01-01&to=2026-01-31`. Internamente vira um intervalo semi-aberto em UTC (`[from 00:00, (to+1 dia) 00:00)`) para o dia final entrar por completo; a filtragem é feita no banco (não em memória), então continua barata mesmo em contas grandes. Um período invertido (`from` depois de `to`) volta **400** no mesmo corpo de erro padrão. Um filtro opcional `type` (`DEPOSIT`/`WITHDRAWAL`/`TRANSFER_IN`/`TRANSFER_OUT`, combinável com o período) restringe o extrato a um único tipo de lançamento — ex.: "só os saques" com `type=WITHDRAWAL`; a filtragem também é feita no banco (mesma query, parâmetro anulável) e um valor de tipo inexistente volta **400** pelo handler de tipo já existente.
- **Observabilidade com Actuator + Micrometer** — `/actuator/health` e `/actuator/info` ficam públicos (úteis para probes de orquestrador/load balancer); `/actuator/metrics` exige token. Além das métricas técnicas (JVM, HTTP), há uma métrica de negócio `bank.account.operations` (counter com tag `type` = `deposit`/`withdrawal`/`transfer`) que conta operações concluídas.
- **Request id (correlation id)** — um filtro (`RequestIdFilter`, primeiro da cadeia) atribui a cada requisição um identificador: usa o cabeçalho `X-Request-Id` enviado pelo cliente/gateway ou gera um UUID quando ausente. O valor entra no `MDC` do SLF4J (aparece em todas as linhas de log da chamada, via `logging.pattern.level`) e volta no cabeçalho `X-Request-Id` da resposta — assim dá pra correlacionar logs espalhados de uma mesma chamada e o cliente tem uma referência ao abrir um chamado de suporte. O MDC é limpo ao final de cada requisição para não vazar o id para a próxima que reutilize a thread.
- **Validação de CPF com dígitos verificadores** — o `document` da conta não é só `\d{11}`: uma constraint customizada (`@Cpf`, com `CpfValidator`) confere os dois dígitos verificadores pelo algoritmo módulo 11 e rejeita números matematicamente impossíveis (ex.: `12345678901`) ou de dígitos repetidos (ex.: `00000000000`) — exatamente o que um banco real recusaria. Como é uma anotação Bean Validation, a checagem roda na borda (DTO) e a falha cai no mesmo corpo de erro padrão (`400`). A entidade JPA não revalida: a validação de entrada é responsabilidade da camada de API.
- **Congelamento de conta (freeze)** — a conta tem um `status` (`ACTIVE`/`BLOCKED`) e endpoints `POST /accounts/{id}/block` e `/unblock`. Uma conta `BLOCKED` rejeita **qualquer** movimentação — depósito, saque e as duas pernas de uma transferência (origem *e* destino) —, refletindo o que um banco faz ao congelar uma conta por suspeita de fraude, ordem judicial ou pedido do titular. A regra mora no domínio (`Account.ensureActive()`, chamada por `deposit`/`withdraw`), então nenhuma operação escapa dela e uma transferência para um destino bloqueado falha **atomicamente** (rollback, ninguém é debitado). A operação barrada volta **422** no corpo de erro padrão; `block`/`unblock` são idempotentes. Migração Flyway `V3__add_account_status.sql` para o profile `postgres`.
- **Travamento otimista no saldo (`@Version`)** — a conta tem uma coluna `version` que o JPA inclui em cada `UPDATE` (`... WHERE version = ?`) e incrementa. Se duas operações concorrentes leem o mesmo saldo e tentam gravá-lo, a segunda atualiza zero linhas e falha — evita o *lost update* (uma operação sobrescrever silenciosamente o saldo da outra). A perdedora recebe **409 Conflict** (mesmo corpo de erro padrão) e pode simplesmente repetir a requisição com o saldo já atualizado. Sem bloqueio pessimista no banco: mais escalável para o caso comum de baixa contenção.
- **Listagem de contas paginada** — `GET /api/v1/accounts` devolve as contas no mesmo envelope `PageResponse` do extrato (`content` + `totalElements`/`totalPages`/`last`), nunca um array cru: o número de contas cresce sem limite, então a listagem sempre vem em páginas (`page`/`size`, com `size` limitado a 1–100 pelas mesmas anotações `@Min`/`@Max` do extrato — um pedido abusivo volta **400**). A ordenação (mais recente primeiro) fica **fixa no servidor** (nome do método do repositório, `findAllByOrderByCreatedAtDesc`), não no `Pageable` — o cliente controla só paginação, nunca a ordem, seguindo a mesma decisão do extrato. Reusa o `PageResponse` e o padrão de validação já existentes, sem inventar formato novo.
- **Idempotência das operações com dinheiro (`Idempotency-Key`)** — depósito, saque e transferência aceitam um cabeçalho opcional `Idempotency-Key`. Quando o cliente sofre um timeout e não sabe se a requisição chegou, ele reenvia com a mesma chave: na primeira vez a operação executa e a resposta é guardada (tabela `idempotency_keys`, a chave é `PRIMARY KEY`); numa repetição a API devolve a **resposta original** sem reexecutar o efeito colateral — nada de depósito ou transferência em duplicidade. O registro da chave é gravado **dentro da mesma transação** da operação, então se ela falha (ex.: saldo insuficiente) o rollback também desfaz a chave: só operações concluídas com sucesso ficam memoizadas e uma tentativa que realmente falhou pode ser refeita com a mesma chave. Sem cabeçalho, o comportamento é o de antes (idempotência é opt-in, retrocompatível). Duas requisições concorrentes com a mesma chave colidem na `PRIMARY KEY` e a segunda volta **409**. Migração Flyway `V4__add_idempotency_keys.sql` para o profile `postgres`.

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
