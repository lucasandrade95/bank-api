package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.account.OperationMetrics.Operation;
import com.lucasandrade.bankapi.account.dto.MoneyOperationRequest;
import com.lucasandrade.bankapi.account.dto.TransferRequest;
import com.lucasandrade.bankapi.shared.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A metrica de negocio {@code bank.account.operations} conta operacoes
 * <b>concluidas</b>, e uma operacao com dinheiro so aconteceu quando a transacao
 * commitou. Contar dentro da transacao contava tambem o que fez rollback — e
 * inflava justamente sob contencao, quando o painel esta sendo olhado.
 *
 * <p>Estes testes prendem a invariante: a contagem acontece <b>depois do commit</b>
 * e nunca depois de um rollback. Ver {@link OperationMetrics}.
 */
@SpringBootTest
class OperationMetricsTest {

    @Autowired
    private OperationMetrics metrics;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // O H2 em memoria e compartilhado entre os testes; gera um documento (CPF)
    // unico por conta para nao colidir com o indice unico de outros testes.
    private static final AtomicLong SEQ = new AtomicLong(700_000);

    private static String uniqueDocument() {
        return String.format("%011d", SEQ.incrementAndGet());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private double count(Operation operation) {
        return meterRegistry.get("bank.account.operations").tag("type", operation.tag()).counter().count();
    }

    @Test
    void countOnCommit_countsOnlyAfterTheTransactionCommits() {
        double before = count(Operation.DEPOSIT);

        transaction().executeWithoutResult(status -> {
            metrics.countOnCommit(Operation.DEPOSIT);
            // Ainda dentro da transacao: nada aconteceu de verdade, nada foi contado.
            assertThat(count(Operation.DEPOSIT)).isEqualTo(before);
        });

        assertThat(count(Operation.DEPOSIT)).isEqualTo(before + 1);
    }

    @Test
    void countOnCommit_countsNothingWhenTheTransactionRollsBack() {
        double before = count(Operation.WITHDRAWAL);

        transaction().executeWithoutResult(status -> {
            metrics.countOnCommit(Operation.WITHDRAWAL);
            status.setRollbackOnly();
        });

        assertThat(count(Operation.WITHDRAWAL)).isEqualTo(before);
    }

    /**
     * Sem transacao ativa nao ha commit para esperar: conta na hora. Um contador
     * que se recusasse a contar seria pior que um contador aproximado.
     */
    @Test
    void countOnCommit_countsImmediatelyWithoutATransaction() {
        double before = count(Operation.TRANSFER);

        metrics.countOnCommit(Operation.TRANSFER);

        assertThat(count(Operation.TRANSFER)).isEqualTo(before + 1);
    }

    /**
     * O caso real que motivou a mudanca: o deposito passa por todas as regras do
     * dominio e chega ao fim do metodo do service, mas a transacao aborta depois
     * disso (no flush/commit — falha otimista, colisao de Idempotency-Key
     * concorrente, falha de infraestrutura). O dinheiro nao entrou; a metrica
     * tambem nao pode dizer que entrou.
     */
    @Test
    void deposit_isNotCounted_whenTheSurroundingTransactionRollsBack() {
        UUID id = accountRepository.save(new Account("Lucas Andrade", uniqueDocument())).getId();
        double before = count(Operation.DEPOSIT);

        transaction().executeWithoutResult(status -> {
            accountService.deposit(id, null, new MoneyOperationRequest(new BigDecimal("100.00")));
            status.setRollbackOnly();
        });

        assertThat(count(Operation.DEPOSIT)).isEqualTo(before);
        assertThat(accountRepository.findById(id).orElseThrow().getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** O caminho feliz continua contando: a operacao committou, entao aconteceu. */
    @Test
    void deposit_isCounted_whenTheTransactionCommits() {
        UUID id = accountRepository.save(new Account("Lucas Andrade", uniqueDocument())).getId();
        double before = count(Operation.DEPOSIT);

        accountService.deposit(id, null, new MoneyOperationRequest(new BigDecimal("100.00")));

        assertThat(count(Operation.DEPOSIT)).isEqualTo(before + 1);
    }

    /**
     * Uma transferencia recusada pelo dominio (saldo insuficiente no destino da
     * regra, aqui na origem) nao chega a contar — a excecao acontece antes. Este
     * teste existe para fixar que o caminho de erro continua sem contar mesmo
     * agora que a contagem e adiada.
     */
    @Test
    void transfer_isNotCounted_whenItFails() {
        UUID sourceId = accountRepository.save(new Account("Origem", uniqueDocument())).getId();
        UUID destinationId = accountRepository.save(new Account("Destino", uniqueDocument())).getId();
        double before = count(Operation.TRANSFER);

        assertThatThrownBy(() -> accountService.transfer(sourceId, null,
                new TransferRequest(destinationId, new BigDecimal("50.00"))))
                .isInstanceOf(BusinessException.class);

        assertThat(count(Operation.TRANSFER)).isEqualTo(before);
    }
}
