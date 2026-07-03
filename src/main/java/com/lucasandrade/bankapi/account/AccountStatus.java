package com.lucasandrade.bankapi.account;

/**
 * Situacao da conta. Uma conta {@code BLOCKED} (congelada) nao movimenta
 * dinheiro — nem debito nem credito — ate ser reativada. Bancos congelam
 * contas por suspeita de fraude, ordem judicial ou pedido do titular.
 */
public enum AccountStatus {
    ACTIVE,
    BLOCKED
}
