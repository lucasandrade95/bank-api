package com.lucasandrade.bankapi.account.dto;

import com.lucasandrade.bankapi.account.Account;
import com.lucasandrade.bankapi.account.AccountStatus;
import com.lucasandrade.bankapi.account.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representacao de saida de uma conta.
 *
 * <p>O {@code document} sai <b>mascarado</b> ({@code ***.456.789-**}) — ver
 * {@link Document}. A mascara e aplicada aqui, no unico ponto onde uma
 * {@link Account} vira resposta, e nao em cada endpoint: e a mesma escolha do
 * {@code Money.normalize}, para que nenhuma rota possa esquecer de aplica-la.
 */
public record AccountResponse(
        UUID id,
        String ownerName,
        String document,
        BigDecimal balance,
        AccountStatus status,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                Document.mask(account.getDocument()),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
