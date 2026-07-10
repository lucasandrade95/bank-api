package com.lucasandrade.bankapi.account;

/** Tipo de movimentacao registrada no extrato da conta. */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_IN,
    TRANSFER_OUT;

    /**
     * Indica se o lancamento e um credito (dinheiro entrando na conta). Deposito
     * e a perna de entrada de uma transferencia somam ao saldo; saque e a perna
     * de saida subtraem. Usado pelo resumo do extrato para totalizar entradas e
     * saidas do periodo.
     */
    public boolean isCredit() {
        return this == DEPOSIT || this == TRANSFER_IN;
    }
}
