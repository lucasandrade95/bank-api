package com.lucasandrade.bankapi.shared;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa a janela de datas isoladamente: a conversao para o intervalo semi-aberto
 * em UTC e a rejeicao de um periodo invertido.
 */
class DateRangeTest {

    @Test
    void convertsInclusiveDatesToHalfOpenUtcInterval() {
        DateRange range = DateRange.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        // Inicio: meia-noite UTC do primeiro dia (inclusivo).
        assertThat(range.fromInstant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        // Fim: meia-noite UTC do dia SEGUINTE ao ultimo, para o dia 31 entrar inteiro.
        assertThat(range.toInstant()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    void nullFromLeavesStartOpen() {
        DateRange range = DateRange.of(null, LocalDate.of(2026, 1, 31));

        assertThat(range.fromInstant()).isNull();
        assertThat(range.toInstant()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    void nullToLeavesEndOpen() {
        DateRange range = DateRange.of(LocalDate.of(2026, 1, 1), null);

        assertThat(range.fromInstant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(range.toInstant()).isNull();
    }

    @Test
    void bothNullLeavesRangeFullyOpen() {
        DateRange range = DateRange.of(null, null);

        assertThat(range.fromInstant()).isNull();
        assertThat(range.toInstant()).isNull();
    }

    @Test
    void sameDayCoversTheWholeDay() {
        LocalDate day = LocalDate.of(2026, 3, 15);
        DateRange range = DateRange.of(day, day);

        assertThat(range.fromInstant()).isEqualTo(Instant.parse("2026-03-15T00:00:00Z"));
        assertThat(range.toInstant()).isEqualTo(Instant.parse("2026-03-16T00:00:00Z"));
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> DateRange.of(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from nao pode ser depois de to");
    }
}
