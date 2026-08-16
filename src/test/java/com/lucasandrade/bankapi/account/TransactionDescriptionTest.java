package com.lucasandrade.bankapi.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Forma canonica da descricao de um lancamento ({@link Transaction#normalizeDescription}). */
class TransactionDescriptionTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void absentOrBlank_isNull(String raw) {
        assertThat(Transaction.normalizeDescription(raw)).isNull();
    }

    @Test
    void surroundingWhitespace_isRemoved_butInnerTextIsKept() {
        assertThat(Transaction.normalizeDescription("  pix do almoco \n")).isEqualTo("pix do almoco");
    }

    @Test
    void alreadyCanonical_isUnchanged() {
        assertThat(Transaction.normalizeDescription("aluguel")).isEqualTo("aluguel");
    }
}
