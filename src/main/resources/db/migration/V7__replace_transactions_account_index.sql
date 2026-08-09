-- Troca o indice de "transactions" por um composto que serve a consulta que existe.
--
-- A V1 criou um indice so por account_id. Ele resolve a igualdade ("os lancamentos
-- desta conta") e para por ai: o extrato pede "esta conta, do mais recente para o
-- mais antigo, uma pagina de 20" (order by created_at desc, id desc + limit), entao
-- o banco ainda precisa ler TODOS os lancamentos da conta e ordena-los para devolver
-- 20 — a pagina 1 custa o mesmo que a pagina 500, e o custo cresce com o tamanho da
-- conta em vez de com o tamanho da pagina. O mesmo vale para as outras duas consultas
-- da tabela, que recortam por periodo: o resumo do extrato e a soma do limite diario
-- de debito (created_at >= ?).
--
-- Com (account_id, created_at, id) o banco posiciona no inicio da conta e caminha
-- pelo indice JA na ordem pedida, parando no limit; o recorte por periodo vira um
-- trecho contiguo do indice. O id entra como terceira coluna pelo mesmo motivo que
-- entra no order by: e o desempate que torna a ordenacao total (ver a decisao de
-- paginacao estavel no README).
CREATE INDEX idx_transactions_account_created_at
    ON transactions (account_id, created_at DESC, id DESC);

-- O indice antigo fica redundante: account_id e o prefixo a esquerda do novo, entao
-- toda busca que o usava continua atendida. Manter os dois so custaria escrita a cada
-- lancamento e espaco em disco. E dropado DEPOIS do CREATE para que a tabela nunca
-- fique sem indice por account_id (a FK para accounts tambem se apoia nele).
DROP INDEX idx_transactions_account_id;
