package com.lucasandrade.bankapi.account;

/**
 * Situacao da conta.
 *
 * <ul>
 *   <li>{@code ACTIVE} — conta normal, movimenta dinheiro.</li>
 *   <li>{@code BLOCKED} — conta congelada: nao movimenta dinheiro (nem debito
 *       nem credito) ate ser reativada. Bancos congelam contas por suspeita de
 *       fraude, ordem judicial ou pedido do titular.</li>
 *   <li>{@code CLOSED} — conta encerrada definitivamente pelo titular. Estado
 *       terminal: nao movimenta dinheiro nem volta a outro status.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    BLOCKED,
    CLOSED
}
