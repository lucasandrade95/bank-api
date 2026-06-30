package com.lucasandrade.bankapi.account.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o algoritmo de validacao de CPF isoladamente (sem contexto Spring),
 * cobrindo os digitos verificadores e os casos de borda.
 */
class CpfValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"11144477735", "12345678062", "98765432029"})
    void acceptsValidCpf(String cpf) {
        assertThat(CpfValidator.isValidCpf(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345678901", // digitos verificadores errados
            "11144477736", // ultimo digito alterado
            "00000000000", // todos iguais
            "11111111111", // todos iguais
            "1234567890",  // 10 digitos (curto)
            "123456789012",// 12 digitos (longo)
            "1114447773a"  // contem letra
    })
    void rejectsInvalidCpf(String cpf) {
        assertThat(CpfValidator.isValidCpf(cpf)).isFalse();
    }

    @Test
    void nullIsConsideredValid_leftToNotBlank() {
        assertThat(new CpfValidator().isValid(null, null)).isTrue();
    }
}
