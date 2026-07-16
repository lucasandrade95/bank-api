package com.lucasandrade.bankapi.shared;

/**
 * A {@code Idempotency-Key} foi reenviada com uma requisicao <b>diferente</b> da
 * que ela ja atendeu (outra conta, outro valor ou outra operacao).
 *
 * <p>Nao e um retry: e reuso indevido da chave. Devolver a resposta guardada
 * seria mentir (a nova operacao nunca aconteceria) e reexecutar quebraria a
 * promessa da chave — entao a API recusa com 409 e o cliente corrige a chave.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
