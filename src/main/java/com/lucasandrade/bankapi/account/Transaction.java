package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "transactions", indexes = @Index(
        name = Transaction.STATEMENT_INDEX,
        columnList = "account_id, created_at desc, id desc"))
public class Transaction {

    /**
     * Indice que serve TODAS as consultas do extrato — e nao apenas o filtro por
     * conta. Declarado aqui (e nao so na migracao Flyway) para que o H2 de
     * dev/teste, cujo schema o Hibernate cria, tenha o mesmo indice do Postgres:
     * um indice que so existe em producao nao e exercitado por ninguem.
     *
     * <p>A ordem das colunas e a coisa toda. Todas as consultas de
     * {@code TransactionRepository} sao "uma conta + uma janela de tempo":
     * o extrato paginado ({@code where account_id = ? ... order by created_at desc,
     * id desc}), o resumo do periodo e a soma do limite diario de debito. Um indice
     * so por {@code account_id} — o que a V1 criou — resolve apenas a igualdade:
     * o banco ainda le TODOS os lancamentos da conta e os ordena em memoria para
     * devolver uma pagina de 20. Numa conta com muito historico isso faz a pagina 1
     * custar o mesmo que a pagina 500, e o custo cresce com o tamanho da conta em
     * vez de com o tamanho da pagina.
     *
     * <p>Com {@code (account_id, created_at, id)} o banco posiciona no inicio da
     * conta e caminha pelo indice JA na ordem pedida, parando no {@code limit} — e
     * o mesmo caminho serve o recorte por periodo ({@code created_at >= ? and < ?}),
     * que passa a ser um trecho contiguo do indice em vez de um filtro linha a linha.
     * O {@code id} entra como terceira coluna pelo mesmo motivo que ele entra no
     * {@code order by}: e o criterio de desempate que torna a ordenacao total.
     *
     * <p>O {@code desc} e explicito por clareza; um btree tambem seria varrido de
     * tras para frente para a mesma ordenacao, ja que {@code account_id} e igualdade.
     */
    static final String STATEMENT_INDEX = "idx_transactions_account_created_at";

    /**
     * Tamanho maximo da descricao livre de um lancamento. Espelhado na coluna
     * ({@code length}) e na validacao de entrada dos payloads ({@code @Size}), para
     * que o limite anunciado ao cliente seja o mesmo que a coluna aceita — um texto
     * que passa na validacao nunca estoura no banco.
     */
    public static final int DESCRIPTION_MAX_LENGTH = 140;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private BigDecimal amount;

    /** Saldo da conta logo apos este lancamento — usado para exibir saldo corrente no extrato. */
    @Column(nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private BigDecimal balanceAfter;

    /** Conta envolvida do outro lado, presente apenas em transferencias. */
    @Column
    private UUID counterpartyAccountId;

    /**
     * Descricao livre informada pelo cliente na operacao ("aluguel", "pix do
     * almoco"), opcional. Guardada ja normalizada ({@link #normalizeDescription}):
     * sem espacos nas pontas e nunca em branco — ausente e {@code null}, nao
     * {@code ""}. Numa transferencia as duas pernas recebem a mesma descricao.
     */
    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Transaction() {
        // exigido pelo JPA
    }

    public Transaction(UUID accountId, TransactionType type, BigDecimal amount,
                       BigDecimal balanceAfter, UUID counterpartyAccountId, String description) {
        this.accountId = accountId;
        this.type = type;
        this.amount = Money.normalize(amount);
        this.balanceAfter = Money.normalize(balanceAfter);
        this.counterpartyAccountId = counterpartyAccountId;
        this.description = normalizeDescription(description);
        this.createdAt = Instant.now();
    }

    /**
     * Forma canonica da descricao: espacos nas pontas removidos e texto em branco
     * tratado como ausente ({@code null}). E o que vai para o banco e tambem o que
     * entra na impressao digital de idempotencia — assim {@code " aluguel "} e
     * {@code "aluguel"} sao a mesma descricao, e {@code ""} e o mesmo que nao mandar.
     */
    static String normalizeDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? null : trimmed;
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

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
