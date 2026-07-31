package com.lucasandrade.bankapi.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Amarra o teto monetario a capacidade real da coluna: {@link Money#MAX} tem de
 * ser o maior valor que cabe em {@code NUMERIC(PRECISION, SCALE)} — nem menos
 * (recusaria valor legitimo), nem mais (deixaria estourar no banco).
 */
class MoneyTest {

    @Test
    void maxIsTheLargestValueTheMonetaryColumnHolds() {
        assertThat(Money.MAX).isEqualByComparingTo(new BigDecimal("99999999999999999.99"));
        assertThat(Money.MAX.scale()).isEqualTo(Money.SCALE);
        // 19 digitos no total: 17 inteiros + 2 centavos, exatamente NUMERIC(19,2).
        assertThat(Money.MAX.precision()).isEqualTo(Money.PRECISION);
    }

    @Test
    void oneCentAboveMaxNoLongerFitsTheColumn() {
        BigDecimal overflow = Money.MAX.add(new BigDecimal("0.01"));

        assertThat(overflow.precision()).isGreaterThan(Money.PRECISION);
    }

    @Test
    void normalizeFixesTheScaleAtTwoDecimals() {
        assertThat(Money.normalize(new BigDecimal("10.5"))).isEqualTo(new BigDecimal("10.50"));
        assertThat(Money.normalize(BigDecimal.ZERO)).isEqualTo(new BigDecimal("0.00"));
        assertThat(Money.normalize(null)).isNull();
    }
}
