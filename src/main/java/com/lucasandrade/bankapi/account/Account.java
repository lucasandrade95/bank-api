package com.lucasandrade.bankapi.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Conta bancaria. Saldo guardado em BigDecimal (nunca double) para evitar
 * erro de arredondamento em operacao financeira.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String ownerName;

    /** Documento do titular (CPF). Unico por conta. */
    @Column(nullable = false, unique = true, length = 11)
    private String document;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Account() {
        // exigido pelo JPA
    }

    public Account(String ownerName, String document) {
        this.ownerName = ownerName;
        this.document = document;
        this.balance = BigDecimal.ZERO;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getDocument() {
        return document;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
