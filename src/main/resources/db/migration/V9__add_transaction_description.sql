-- Descricao livre e opcional de um lancamento ("aluguel", "pix do almoco"),
-- informada pelo cliente no deposito/saque/transferencia e exibida no extrato.
-- Espelha Transaction.description: nula quando o cliente nao informa (nunca ""),
-- e o tamanho e o mesmo do @Size do payload (Transaction.DESCRIPTION_MAX_LENGTH),
-- para que um texto que passa na validacao nunca estoure na coluna.
-- Lancamentos antigos ficam sem descricao (NULL) — nao ha como inventa-la depois.
ALTER TABLE transactions ADD COLUMN description VARCHAR(140);
