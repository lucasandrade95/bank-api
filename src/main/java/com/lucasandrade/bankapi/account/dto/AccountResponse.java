package com.lucasandrade.bankapi.account.dto;

import com.lucasandrade.bankapi.account.Account;
import com.lucasandrade.bankapi.account.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Representacao de saida de uma conta. */
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
                account.getDocument(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
