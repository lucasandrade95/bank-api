package com.lucasandrade.bankapi.account.dto;

import com.lucasandrade.bankapi.account.Transaction;
import com.lucasandrade.bankapi.account.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Linha do extrato: um lancamento e o saldo resultante na conta. */
public record TransactionResponse(
        UUID id,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        UUID counterpartyAccountId,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCounterpartyAccountId(),
                transaction.getCreatedAt()
        );
    }
}
