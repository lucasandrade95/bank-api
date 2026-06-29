package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.shared.GlobalExceptionHandler;
import com.lucasandrade.bankapi.shared.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Travamento otimista da conta (@Version). Garante que duas escritas concorrentes
 * sobre o mesmo saldo nao se sobrescrevem silenciosamente (lost update): a
 * perdedora falha e o handler global a traduz em 409 Conflict.
 */
@SpringBootTest
class AccountOptimisticLockTest {

    @Autowired
    private AccountRepository repository;

    // O H2 em memoria e compartilhado entre os testes; gera um documento (CPF)
    // unico por conta para nao colidir com o indice unico de outros testes.
    private static final AtomicLong SEQ = new AtomicLong();

    private static String uniqueDocument() {
        return String.format("%011d", SEQ.incrementAndGet());
    }

    @Test
    void everySave_incrementsVersion() {
        Account saved = repository.save(new Account("Lucas Andrade", uniqueDocument()));
        assertThat(saved.getVersion()).isZero();

        Account loaded = repository.findById(saved.getId()).orElseThrow();
        loaded.deposit(new BigDecimal("100.00"));
        Account afterDeposit = repository.saveAndFlush(loaded);

        assertThat(afterDeposit.getVersion()).isEqualTo(1L);
    }

    @Test
    void staleUpdate_throwsOptimisticLockingFailure() {
        UUID id = repository.save(new Account("Lucas Andrade", uniqueDocument())).getId();

        // Copia "stale": lida antes de qualquer alteracao concorrente (version 0).
        Account stale = repository.findById(id).orElseThrow();

        // Outra transacao deposita primeiro e sobe a version para 1.
        Account fresh = repository.findById(id).orElseThrow();
        fresh.deposit(new BigDecimal("100.00"));
        repository.saveAndFlush(fresh);

        // Gravar a copia stale (version 0) sobre a version 1 deve falhar.
        stale.deposit(new BigDecimal("50.00"));
        assertThatThrownBy(() -> repository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void handlerTranslatesOptimisticLockTo409() {
        ResponseEntity<ApiError> response = new GlobalExceptionHandler()
                .handleOptimisticLock(new ObjectOptimisticLockingFailureException(Account.class, UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().messages())
                .containsExactly("Conta alterada concorrentemente, tente novamente");
    }
}
