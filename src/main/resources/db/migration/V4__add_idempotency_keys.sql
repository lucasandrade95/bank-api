-- Tabela de chaves de idempotencia. Espelha a entidade IdempotencyRecord:
-- guarda a Idempotency-Key enviada pelo cliente e a resposta original (JSON),
-- para que uma repeticao da mesma requisicao (ex.: retry apos timeout) devolva
-- o mesmo resultado sem reexecutar o efeito colateral (deposito/saque/transferencia
-- em duplicidade). A chave (id) e PRIMARY KEY, entao uma segunda gravacao
-- concorrente da mesma chave viola a unicidade e a operacao duplicada e barrada.
CREATE TABLE idempotency_keys (
    id            VARCHAR(255) PRIMARY KEY,
    response_body VARCHAR(4000) NOT NULL,
    created_at    TIMESTAMP NOT NULL
);
