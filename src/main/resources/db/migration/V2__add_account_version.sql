-- Coluna de travamento otimista da conta. Espelha o campo @Version da entidade
-- Account: o Hibernate usa "WHERE version = ?" no UPDATE e incrementa o valor,
-- bloqueando o lost update quando duas operacoes concorrentes alteram o mesmo saldo.
ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
