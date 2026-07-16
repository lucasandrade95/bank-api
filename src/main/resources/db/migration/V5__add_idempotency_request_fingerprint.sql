-- Vincula cada Idempotency-Key a requisicao que a gerou, guardando a impressao
-- digital dela (SHA-256 em hex, 64 chars). Espelha IdempotencyRecord.requestFingerprint.
--
-- Sem esta coluna a chave era global: reenviada com OUTRA requisicao (outra conta,
-- outro valor, outra operacao), ela devolvia a resposta guardada da operacao
-- anterior e a nova nunca acontecia — um saque "confirmado" que nunca debitou.
-- Com ela, uma repeticao so recebe a resposta guardada se a requisicao bater;
-- reuso da chave em outro pedido volta 409.
--
-- Linhas antigas (anteriores a esta migracao) nao tem impressao digital: recebem
-- '' pelo DEFAULT do backfill, que nunca bate com um SHA-256 real. Na pratica um
-- retry de chave pre-migracao volta 409 em vez da resposta guardada — falha
-- conservadora (o cliente refaz com chave nova) e nao um resultado errado.
ALTER TABLE idempotency_keys
    ADD COLUMN request_fingerprint VARCHAR(64) NOT NULL DEFAULT '';

-- DEFAULT era so para o backfill: novas linhas devem sempre trazer a impressao digital.
ALTER TABLE idempotency_keys
    ALTER COLUMN request_fingerprint DROP DEFAULT;
