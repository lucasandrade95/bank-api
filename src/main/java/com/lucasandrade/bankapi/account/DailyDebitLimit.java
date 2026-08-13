package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.shared.BusinessException;
import com.lucasandrade.bankapi.shared.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Limite diario de debito da conta: o quanto pode sair dela num mesmo dia,
 * somando saques e transferencias enviadas. E o controle que todo banco de
 * verdade tem — limita o estrago de uma conta comprometida (senha vazada,
 * celular roubado), que sem teto poderia ser esvaziada de uma vez.
 *
 * <p>Credito nao entra na conta do limite: deposito e transferencia recebida
 * nao tiram dinheiro do titular. A classificacao vem de
 * {@link TransactionType#debitTypes()}, nao de uma lista repetida aqui.
 *
 * <p>O dia e o dia <b>UTC</b>, mesmo referencial dos filtros de periodo do
 * extrato ({@code DateRange}) — o limite zera a meia-noite UTC, nao no fuso de
 * quem chama nem no do servidor, entao a janela e a mesma independente de onde a
 * aplicacao roda.
 *
 * <p>Value object de proposito: a decisao ("cabe no que sobrou?") e aritmetica
 * pura e testavel sem banco. Quem busca <i>quanto ja saiu hoje</i> e o service,
 * que tem o repositorio — a soma e feita no banco, nunca em memoria.
 */
public record DailyDebitLimit(BigDecimal limit) {

    public DailyDebitLimit {
        if (limit == null || limit.signum() < 0) {
            // Erro de configuracao, nao de requisicao: falha no start da aplicacao.
            throw new IllegalArgumentException("Limite diario de debito deve ser um valor nao negativo");
        }
        limit = Money.normalize(limit);
    }

    /**
     * Inicio da janela do limite: meia-noite UTC do dia corrente. O fim da janela
     * nao precisa ser filtrado — nao existe lancamento no futuro.
     */
    public static Instant windowStart() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * O quanto ainda pode sair da conta hoje, dado o total ja debitado na janela.
     * Nunca negativo: um total acima do limite (ex.: o teto configurado baixou
     * depois de o dinheiro ja ter saido) devolve zero, nao um valor negativo.
     *
     * <p>E a MESMA aritmetica que decide se uma operacao passa ({@link #ensureAllows}):
     * o que a consulta de limites informa e o que a proxima operacao vai obedecer.
     */
    public BigDecimal remaining(BigDecimal usedInWindow) {
        return Money.normalize(limit.subtract(usedInWindow).max(BigDecimal.ZERO));
    }

    /**
     * Verifica se um novo debito cabe no que sobrou do limite de hoje, dado o
     * total ja debitado na janela. Estourar o limite e uma regra de negocio, nao
     * um erro de formato: sai como {@link BusinessException} (422), do mesmo jeito
     * que saldo insuficiente.
     *
     * <p>A mensagem devolve o quanto ainda ha disponivel hoje, para o cliente
     * poder reenviar um valor que passa em vez de ficar tentando as cegas.
     */
    public void ensureAllows(BigDecimal usedInWindow, BigDecimal amount) {
        BigDecimal remaining = remaining(usedInWindow);
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessException(
                    "Limite diario de debito excedido; disponivel hoje: " + remaining);
        }
    }
}
