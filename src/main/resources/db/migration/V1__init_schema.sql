-- Esquema inicial do Bank API, gerenciado por Flyway no profile "postgres".
-- Espelha as entidades JPA (users, accounts, transactions). Nomes de coluna
-- seguem a estrategia padrao do Spring (camelCase -> snake_case).

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    username      VARCHAR(60) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP NOT NULL
);

CREATE TABLE accounts (
    id         UUID PRIMARY KEY,
    owner_name VARCHAR(255) NOT NULL,
    document   VARCHAR(11) NOT NULL UNIQUE,
    balance    NUMERIC(19, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE transactions (
    id                      UUID PRIMARY KEY,
    account_id              UUID NOT NULL,
    type                    VARCHAR(20) NOT NULL,
    amount                  NUMERIC(19, 2) NOT NULL,
    balance_after           NUMERIC(19, 2) NOT NULL,
    counterparty_account_id UUID,
    created_at              TIMESTAMP NOT NULL,
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

-- Extrato e sempre consultado por conta; indice acelera o GET /statement.
CREATE INDEX idx_transactions_account_id ON transactions (account_id);
