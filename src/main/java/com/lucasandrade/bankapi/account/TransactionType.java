package com.lucasandrade.bankapi.account;

import java.util.Arrays;
import java.util.List;

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

    /**
     * Tipos que retiram dinheiro da conta (saque e a perna de saida de uma
     * transferencia). Derivado de {@link #isCredit()} para a classificacao
     * credito/debito continuar morando num unico lugar: incluir um tipo novo no
     * enum ja o coloca do lado certo, sem lista para esquecer de atualizar.
     *
     * <p>Usado pelo limite diario de debito, que soma so o que sai da conta.
     */
    public static List<TransactionType> debitTypes() {
        return DEBIT_TYPES;
    }

    private static final List<TransactionType> DEBIT_TYPES =
            Arrays.stream(values()).filter(type -> !type.isCredit()).toList();
}
