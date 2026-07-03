-- Situacao da conta. Espelha o enum AccountStatus (@Enumerated(STRING)) da
-- entidade Account: uma conta BLOCKED (congelada) nao movimenta dinheiro ate
-- ser reativada. Contas existentes nascem ACTIVE (default).
ALTER TABLE accounts ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
