-- Escopa a Idempotency-Key ao cliente que a enviou: a chave primaria passa a ser
-- o par (client_id, id) em vez da chave sozinha.
--
-- Uma Idempotency-Key e o nome que AQUELE cliente deu a uma requisicao DELE, nao
-- um identificador global. Com a chave sozinha na PRIMARY KEY, a string escolhida
-- por um cliente decidia o destino da requisicao de outro: no melhor caso a
-- impressao digital (V5) nao batia e o segundo recebia 409 numa requisicao que ele
-- nunca tinha enviado; no pior ela batia (mesma operacao, mesma conta, mesmo valor)
-- e ele recebia 200 com a resposta guardada do primeiro — a operacao dele nunca
-- acontecia e nada indicava o erro. A impressao digital nao cobre isso: ela responde
-- "e a mesma requisicao?", nunca "e o mesmo cliente?".
--
-- client_id e o hash SHA-256 do username autenticado (largura fixa, mesma forma de
-- request_fingerprint): a tabela so precisa responder "foi o mesmo cliente?", entao
-- ela nao vira uma copia do cadastro de usuarios.
--
-- Trocar a coluna da PRIMARY KEY exige recriar a tabela — nao ha DDL portavel entre
-- PostgreSQL e H2 para dropar uma PK nomeada pelo banco. Aqui isso e barato de
-- proposito: esta tabela e um CACHE de respostas com 24h de retencao (V6), nao um
-- livro-razao — quem guarda o historico do dinheiro e "transactions". As linhas
-- existentes sao preservadas sob o escopo sentinela '-' (o mesmo de "sem cliente
-- autenticado"): elas nao respondem mais a um retry, mas saem sozinhas no proximo
-- expurgo. A janela de risco e um retry que atravesse o deploy — a mesma janela de
-- qualquer mudanca de schema, e menor que a retencao das chaves.

CREATE TABLE idempotency_keys_scoped (
    client_id           VARCHAR(64) NOT NULL,
    id                  VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    response_body       VARCHAR(4000) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    PRIMARY KEY (client_id, id)
);

INSERT INTO idempotency_keys_scoped (client_id, id, request_fingerprint, response_body, created_at)
SELECT '-', id, request_fingerprint, response_body, created_at FROM idempotency_keys;

DROP TABLE idempotency_keys;

ALTER TABLE idempotency_keys_scoped RENAME TO idempotency_keys;

-- Recriado porque o indice da V6 morreu junto com a tabela antiga. O expurgo
-- periodico filtra por created_at; sem indice ele varre a tabela inteira.
CREATE INDEX idx_idempotency_keys_created_at ON idempotency_keys (created_at);
