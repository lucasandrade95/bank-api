package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.account.dto.AccountResponse;
import com.lucasandrade.bankapi.account.dto.CreateAccountRequest;
import com.lucasandrade.bankapi.account.dto.MoneyOperationRequest;
import com.lucasandrade.bankapi.account.dto.TransferRequest;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;

/**
 * Ordem de aquisicao das contas numa transferencia.
 *
 * <p>Uma transferencia atualiza duas linhas de {@code accounts} na mesma transacao,
 * e cada UPDATE segura um lock na linha ate o commit. Se cada transferencia pegasse
 * as contas na sua propria ordem (origem, depois destino), duas transferencias
 * simultaneas em sentidos opostos entre as MESMAS contas travariam uma na outra —
 * deadlock, com o banco matando uma delas. Carregando sempre na mesma ordem
 * (pelo UUID), o ciclo nao se forma: a segunda so espera a primeira terminar.
 *
 * <p>Um deadlock de verdade nao e reproduzivel de forma deterministica num teste,
 * entao o que se verifica aqui e a INVARIANTE que o evita: a ordem em que as contas
 * sao carregadas nao depende do sentido da transferencia. E a ordem de carga que
 * importa — as contas ja estao gerenciadas quando {@code save} e chamado, entao os
 * UPDATEs saem no flush, na ordem em que as entidades entraram no contexto.
 */
@SpringBootTest
class AccountTransferLockOrderTest {

    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Autowired
    private AccountService service;

    /**
     * Espiao do repositorio: serve para observar a ORDEM das chamadas a
     * {@code findById}, sem alterar o comportamento real (o spy delega).
     */
    @SpyBean
    private AccountRepository repository;

    @Test
    void transfer_loadsAccountsInCanonicalOrder_regardlessOfDirection() {
        UUID first = accountWithBalance("Titular A", "50010010050");
        UUID second = accountWithBalance("Titular B", "50010010130");

        // Qual conta tem o menor UUID e sorteado pelo banco: descobrimos aqui.
        UUID lower = first.compareTo(second) < 0 ? first : second;
        UUID higher = lower.equals(first) ? second : first;
        assertThat(lower).isLessThan(higher);

        // Sentido "menor -> maior": a ordem natural ja seria a canonica.
        clearInvocations(repository);
        service.transfer(lower, null, new TransferRequest(higher, TEN, null));
        assertLoadOrder(lower, higher);

        // Sentido inverso: e aqui que a ordem natural (origem primeiro) divergiria.
        clearInvocations(repository);
        service.transfer(higher, null, new TransferRequest(lower, TEN, null));
        assertLoadOrder(lower, higher);
    }

    /** A transferencia continua funcionando normalmente no sentido "maior -> menor". */
    @Test
    void transfer_reverseDirection_stillMovesMoney() {
        UUID first = accountWithBalance("Titular C", "50010010211");
        UUID second = accountWithBalance("Titular D", "50010010300");

        UUID lower = first.compareTo(second) < 0 ? first : second;
        UUID higher = lower.equals(first) ? second : first;

        service.transfer(higher, null, new TransferRequest(lower, TEN, null));

        assertThat(service.findById(higher).balance()).isEqualByComparingTo("90.00");
        assertThat(service.findById(lower).balance()).isEqualByComparingTo("110.00");
    }

    private void assertLoadOrder(UUID expectedFirst, UUID expectedSecond) {
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).findById(expectedFirst);
        inOrder.verify(repository).findById(expectedSecond);
    }

    /** Cria uma conta com saldo suficiente para transferir. */
    private UUID accountWithBalance(String ownerName, String document) {
        AccountResponse account = service.create(null, new CreateAccountRequest(ownerName, document));
        service.deposit(account.id(), null, new MoneyOperationRequest(new BigDecimal("100.00"), null));
        return account.id();
    }
}
