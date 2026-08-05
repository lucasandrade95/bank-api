package com.lucasandrade.bankapi.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mascaramento do CPF para exibicao. Teste unitario rapido (sem contexto Spring):
 * a regra e aritmetica de string pura.
 */
class DocumentTest {

    @Test
    void mask_hidesFirstThreeDigitsAndCheckDigits() {
        assertThat(Document.mask("12345678901")).isEqualTo("***.456.789-**");
    }

    @Test
    void mask_keepsOnlyTheMiddleSixDigits() {
        // nenhum digito escondido pode aparecer na saida
        String masked = Document.mask("11144477735");

        assertThat(masked).isEqualTo("***.444.777-**");
        assertThat(masked).doesNotContain("111").doesNotContain("35");
    }

    @Test
    void mask_nullStaysNull() {
        assertThat(Document.mask(null)).isNull();
    }

    /**
     * Entrada fora do formato nao pode "passar batido" pelo mascaramento: uma
     * funcao que devolve o valor cru quando nao reconhece a entrada nao protege
     * justamente o caso inesperado. Fail closed.
     */
    @Test
    void mask_unexpectedFormat_isFullyMasked() {
        assertThat(Document.mask("123")).isEqualTo(Document.FULLY_MASKED);
        assertThat(Document.mask("123456789012")).isEqualTo(Document.FULLY_MASKED);
        assertThat(Document.mask("1234567890a")).isEqualTo(Document.FULLY_MASKED);
        assertThat(Document.mask("")).isEqualTo(Document.FULLY_MASKED);
    }
}
