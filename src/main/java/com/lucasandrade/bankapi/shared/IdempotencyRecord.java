package com.lucasandrade.bankapi.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de uma operacao ja processada, identificada pela {@code Idempotency-Key}
 * enviada pelo cliente. Guarda a resposta original (JSON) para que uma repeticao
 * da mesma requisicao — tipica quando o cliente sofre timeout e tenta de novo —
 * devolva o mesmo resultado sem executar o efeito colateral duas vezes
 * (evita, por exemplo, um deposito ou uma transferencia em duplicidade).
 *
 * <p>O registro e gravado dentro da mesma transacao da operacao: se a operacao
 * falha (ex.: saldo insuficiente), o rollback tambem desfaz a chave — entao so
 * operacoes concluidas com sucesso ficam memoizadas, e uma tentativa que
 * realmente falhou pode ser refeita com a mesma chave.
 *
 * <p>Junto da resposta guarda a "impressao digital" da requisicao que gerou a
 * chave, para que a chave so responda a repeticoes daquela mesma requisicao —
 * ver {@link IdempotencyService}.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    /** A propria {@code Idempotency-Key} enviada pelo cliente (unica por operacao). */
    @Id
    @Column(length = 255)
    private String id;

    /**
     * Hash SHA-256 (hex) da requisicao que gerou esta chave: identifica operacao,
     * conta e valor. Uma repeticao so recebe a resposta guardada se a impressao
     * digital bater — reuso da chave com outra requisicao vira 409.
     */
    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    /** Corpo da resposta original serializado em JSON, devolvido nas repeticoes. */
    @Column(nullable = false, length = 4000)
    private String responseBody;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected IdempotencyRecord() {
        // exigido pelo JPA
    }

    public IdempotencyRecord(String id, String requestFingerprint, String responseBody) {
        this.id = id;
        this.requestFingerprint = requestFingerprint;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
