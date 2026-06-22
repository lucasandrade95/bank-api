package com.lucasandrade.bankapi.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lancamento imutavel no extrato de uma conta. Cada deposito, saque ou perna
 * de transferencia gera um registro com o valor movido e o saldo resultante
 * ({@code balanceAfter}), permitindo reconstruir o historico da conta.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Saldo da conta logo apos este lancamento — usado para exibir saldo corrente no extrato. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    /** Conta envolvida do outro lado, presente apenas em transferencias. */
    @Column
    private UUID counterpartyAccountId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Transaction() {
        // exigido pelo JPA
    }

    public Transaction(UUID accountId, TransactionType type, BigDecimal amount,
                       BigDecimal balanceAfter, UUID counterpartyAccountId) {
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.counterpartyAccountId = counterpartyAccountId;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public UUID getCounterpartyAccountId() {
        return counterpartyAccountId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
