package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.shared.BusinessException;
import com.lucasandrade.bankapi.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

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
    private BigDecimal balance = Money.normalize(BigDecimal.ZERO);

    /**
     * Situacao da conta. Uma conta {@code BLOCKED} rejeita qualquer movimentacao
     * (deposito, saque e as duas pernas de transferencia) — a conta esta congelada.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * Versao para travamento otimista. O JPA inclui {@code WHERE version = ?} em
     * cada UPDATE e incrementa a coluna; se duas transacoes leem o mesmo saldo e
     * tentam grava-lo, a segunda atualiza 0 linhas e o Hibernate lanca
     * {@code ObjectOptimisticLockingFailureException} — evita o lost update
     * (uma operacao concorrente sobrescrever o saldo da outra).
     */
    @Version
    private long version;

    protected Account() {
        // exigido pelo JPA
    }

    public Account(String ownerName, String document) {
        this.ownerName = ownerName;
        this.document = document;
        this.balance = Money.normalize(BigDecimal.ZERO);
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    /**
     * Credita um valor no saldo. O valor deve ser positivo — regra reforcada
     * aqui no dominio, independente da validacao de entrada.
     */
    public void deposit(BigDecimal amount) {
        ensureActive();
        requirePositive(amount);
        this.balance = Money.normalize(this.balance.add(amount));
    }

    /**
     * Debita um valor do saldo. Rejeita saque sem saldo suficiente
     * (sem cheque especial nesta versao).
     */
    public void withdraw(BigDecimal amount) {
        ensureActive();
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException("Saldo insuficiente");
        }
        this.balance = Money.normalize(this.balance.subtract(amount));
    }

    /** Congela a conta: nenhuma movimentacao passa a ser permitida. Idempotente. */
    public void block() {
        ensureNotClosed();
        this.status = AccountStatus.BLOCKED;
    }

    /** Reativa a conta, voltando a permitir movimentacao. Idempotente. */
    public void unblock() {
        ensureNotClosed();
        this.status = AccountStatus.ACTIVE;
    }

    /**
     * Encerra a conta definitivamente. So e permitido com saldo zero — o titular
     * precisa sacar ou transferir todo o saldo antes. Depois de encerrada e um
     * estado terminal: nenhuma movimentacao ou mudanca de status e aceita.
     * Idempotente: encerrar uma conta ja encerrada nao faz nada.
     */
    public void close() {
        if (status == AccountStatus.CLOSED) {
            return;
        }
        if (balance.signum() != 0) {
            throw new BusinessException("Nao e possivel encerrar conta com saldo diferente de zero");
        }
        this.status = AccountStatus.CLOSED;
    }

    private void ensureActive() {
        switch (status) {
            case BLOCKED -> throw new BusinessException("Conta bloqueada; operacao nao permitida");
            case CLOSED -> throw new BusinessException("Conta encerrada; operacao nao permitida");
            default -> { /* ACTIVE: operacao permitida */ }
        }
    }

    /** Uma conta encerrada e terminal: nao aceita mais mudanca de status. */
    private void ensureNotClosed() {
        if (status == AccountStatus.CLOSED) {
            throw new BusinessException("Conta encerrada; operacao nao permitida");
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Valor da operacao deve ser positivo");
        }
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

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
