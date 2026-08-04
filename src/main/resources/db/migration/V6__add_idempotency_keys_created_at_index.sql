-- Indice para o expurgo periodico das Idempotency-Key vencidas
-- (IdempotencyService.purgeExpired, "delete ... where created_at < :cutoff").
--
-- Sem ele o expurgo e um sequential scan na tabela inteira a cada execucao —
-- justo a tabela que toda operacao com dinheiro le e escreve. Com o indice, o
-- delete toca apenas as linhas que realmente venceram.
CREATE INDEX idx_idempotency_keys_created_at ON idempotency_keys (created_at);
