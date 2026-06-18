# Progresso

- **2026-06-18** — Adicionei depósito e saque (`POST /api/v1/accounts/{id}/deposit` e `/withdraw`). As regras de saldo ficam no domínio (entidade `Account`): depósito e saque exigem valor positivo e o saque rejeita saldo insuficiente (HTTP 422). Validação de entrada com `@Positive`/`@Digits` na DTO `MoneyOperationRequest` (valor em `BigDecimal`). Cobertura com 5 novos testes MockMvc: depósito, saque dentro do saldo, saldo insuficiente, valor negativo e conta inexistente.
