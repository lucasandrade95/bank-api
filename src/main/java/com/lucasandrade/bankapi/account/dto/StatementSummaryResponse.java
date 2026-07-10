package com.lucasandrade.bankapi.account.dto;

import com.lucasandrade.bankapi.account.TransactionType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Resumo do extrato de uma conta em um periodo: totais consolidados em vez da
 * lista de lancamentos. Util para o cliente montar um painel ("quanto entrou e
 * saiu no mes?") sem baixar o extrato inteiro e somar do lado dele.
 *
 * <p>{@code totalIn} soma os creditos (deposito e transferencia recebida) e
 * {@code totalOut} os debitos (saque e transferencia enviada); {@code net} e a
 * diferenca (positiva se entrou mais do que saiu). {@code byType} detalha a
 * contagem e o total de cada tipo de lancamento presente no periodo.
 */
public record StatementSummaryResponse(
        long totalCount,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal net,
        Map<TransactionType, TypeBreakdown> byType
) {
    /** Contagem e soma dos valores de um unico tipo de lancamento. */
    public record TypeBreakdown(long count, BigDecimal total) {
    }
}
