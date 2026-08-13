package com.lucasandrade.bankapi.account.dto;

import java.math.BigDecimal;

/**
 * Situacao dos limites operacionais de uma conta. Hoje existe um unico limite
 * (o de debito diario), mas o envelope e um objeto de proposito: um limite novo
 * (ex.: teto por transacao) entra como campo irmao, sem quebrar o contrato.
 *
 * <p>Existe para o cliente poder perguntar "quanto ainda posso movimentar hoje?"
 * antes de tentar a operacao, em vez de descobrir o limite estourando-o (422).
 */
public record AccountLimitsResponse(DailyDebit dailyDebit) {

    /**
     * O limite diario de debito: o teto configurado, o quanto ja saiu da conta
     * na janela de hoje (dia UTC) e o quanto ainda ha disponivel.
     */
    public record DailyDebit(BigDecimal limit, BigDecimal usedToday, BigDecimal availableToday) {
    }
}
