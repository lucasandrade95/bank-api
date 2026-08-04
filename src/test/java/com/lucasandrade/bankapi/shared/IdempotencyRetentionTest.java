package com.lucasandrade.bankapi.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retencao das {@code Idempotency-Key}: a tabela de chaves e um cache de
 * respostas, entao as vencidas sao expurgadas em vez de ficarem para sempre.
 *
 * <p>O que precisa ser verdade e assimetrico: apagar uma chave vencida so libera
 * espaco, mas apagar uma chave que o cliente ainda pode repetir faz o retry
 * <b>reexecutar a operacao</b> (deposito em duplicidade). Por isso os dois testes
 * abaixo: um confirma que o vencido sai, o outro que o que ainda vale fica e
 * continua devolvendo a resposta guardada.
 */
@SpringBootTest
class IdempotencyRetentionTest {

    @Autowired
    private IdempotencyService idempotency;

    @Autowired
    private IdempotencyRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Grava uma chave e envelhece o registro no banco, simulando a passagem do tempo. */
    private String keyCreatedAgo(Duration age) {
        String key = "ret-" + UUID.randomUUID();
        idempotency.execute(key, "deposit|conta|100.00", String.class, () -> "ok");
        jdbcTemplate.update("update idempotency_keys set created_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(age)), key);
        return key;
    }

    @Test
    void purgeRemovesKeysOlderThanRetention_andKeepsRecentOnes() {
        String expired = keyCreatedAgo(Duration.ofHours(25));
        String recent = keyCreatedAgo(Duration.ofHours(23));

        assertThat(idempotency.purgeExpired()).isPositive();

        assertThat(repository.findById(expired)).isEmpty();
        assertThat(repository.findById(recent)).isPresent();
    }

    /**
     * A garantia que importa: enquanto a chave esta dentro da janela, o expurgo
     * nao a toca e um retry continua recebendo a resposta original — sem
     * reexecutar o efeito colateral.
     */
    @Test
    void purgeDoesNotBreakReplayOfKeyStillInsideWindow() {
        String key = keyCreatedAgo(Duration.ofHours(1));
        AtomicInteger executions = new AtomicInteger();

        idempotency.purgeExpired();

        String replayed = idempotency.execute(key, "deposit|conta|100.00", String.class,
                () -> {
                    executions.incrementAndGet();
                    return "reexecutado";
                });

        assertThat(replayed).isEqualTo("ok");
        assertThat(executions).hasValue(0);
    }
}
