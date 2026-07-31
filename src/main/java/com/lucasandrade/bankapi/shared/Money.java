package com.lucasandrade.bankapi.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Normaliza todo valor monetario para exatamente 2 casas decimais (centavos) — a
 * mesma escala das colunas de saldo/valor no banco ({@code precision = 19, scale = 2}).
 *
 * <p>Garante que a API sempre devolva dinheiro no formato "0.00": um deposito de
 * {@code 10.5} vira {@code 10.50}, e dois depositos de {@code 10.5} nao viram
 * {@code 21.0} — em vez de refletir a escala crua do que o cliente mandou (que
 * {@link BigDecimal} preserva). Sem isto, respostas diferentes exibiriam o mesmo
 * valor com escalas diferentes.
 *
 * <p>Usa {@link RoundingMode#HALF_EVEN} (arredondamento bancario). Como a entrada
 * ja e validada para no maximo 2 casas decimais ({@code @Digits(fraction = 2)}),
 * na pratica isto so completa zeros a direita, nunca arredonda de fato — o modo de
 * arredondamento e uma salvaguarda, nao um caminho ativo.
 */
public final class Money {

    /** Casas decimais de todo valor monetario (centavos). */
    public static final int SCALE = 2;

    /**
     * Digitos totais de toda coluna monetaria. Constante de compilacao para poder
     * ser usada nas proprias anotacoes {@code @Column(precision = ..., scale = ...)}
     * das entidades — assim a capacidade da coluna e a regra que a protege
     * ({@link #MAX}) saem da mesma fonte, e mudar uma muda a outra.
     */
    public static final int PRECISION = 19;

    /**
     * Maior valor monetario representavel: o que cabe em {@code NUMERIC(19,2)},
     * ou seja {@code 99999999999999999.99}. Passar disto nao e "um numero grande",
     * e um valor que <b>nao cabe na coluna</b> — a gravacao falharia no banco.
     * Por isso o dominio recusa antes ({@code Account.deposit}), com uma regra de
     * negocio clara, em vez de deixar estourar no flush.
     */
    public static final BigDecimal MAX = BigDecimal.ONE
            .movePointRight(PRECISION - SCALE)          // 10^17
            .subtract(BigDecimal.ONE.movePointLeft(SCALE)) // menos um centavo
            .setScale(SCALE, RoundingMode.UNNECESSARY);

    private Money() {
    }

    /** Devolve o valor com escala 2; {@code null} continua {@code null}. */
    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_EVEN);
    }
}
