package com.lucasandrade.bankapi.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O indice que serve o extrato existe no schema que o Hibernate cria (H2 de
 * dev/teste), e nao apenas na migracao Flyway do profile "postgres".
 *
 * <p>Um indice e uma decisao que se duplica facil e se esquece facil: quem mexe na
 * consulta do extrato precisa mexer no indice, e ha DOIS lugares onde ele vive (a
 * anotacao da entidade e o SQL da migracao). Este teste prende a metade que ninguem
 * olha — se o {@code @Index} sair da entidade, o H2 continua funcionando (indice nao
 * muda resultado, so custo) e nada quebraria; o teste quebra.
 *
 * <p>O que e afirmado e a <b>ordem das colunas</b>, nao a mera existencia: e a ordem
 * que faz o indice servir a consulta. Com {@code account_id} na frente o banco
 * posiciona na conta e caminha ja na ordem do {@code order by}, parando no
 * {@code limit}; invertida, o indice viraria enfeite. O custo real de um plano de
 * execucao nao e afirmavel de forma deterministica num teste — entao o que se fixa
 * aqui e a estrutura que o torna possivel.
 */
@SpringBootTest
class TransactionStatementIndexTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void statementIndexCoversAccountThenCreatedAtThenId() {
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.index_columns " +
                        "where table_name = 'TRANSACTIONS' and index_name = ? " +
                        "order by ordinal_position",
                String.class,
                Transaction.STATEMENT_INDEX.toUpperCase());

        assertThat(columns).containsExactly("ACCOUNT_ID", "CREATED_AT", "ID");
    }
}
