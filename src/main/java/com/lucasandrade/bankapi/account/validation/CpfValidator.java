package com.lucasandrade.bankapi.account.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementa a validacao de {@link Cpf}: confere os dois digitos verificadores
 * do CPF pelo algoritmo modulo 11 com pesos decrescentes.
 */
public class CpfValidator implements ConstraintValidator<Cpf, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // ausencia/obrigatoriedade fica com @NotBlank
        }
        return isValidCpf(value);
    }

    /** {@code true} se {@code cpf} tem 11 digitos e os verificadores conferem. */
    static boolean isValidCpf(String cpf) {
        if (cpf.length() != 11 || !cpf.chars().allMatch(Character::isDigit)) {
            return false;
        }
        // Digitos todos iguais (ex.: 11111111111) satisfazem o calculo modulo 11,
        // mas nao sao CPFs validos — rejeitamos explicitamente.
        if (cpf.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(cpf, 9) == (cpf.charAt(9) - '0')
                && checkDigit(cpf, 10) == (cpf.charAt(10) - '0');
    }

    /**
     * Calcula o digito verificador esperado a partir dos primeiros {@code length}
     * digitos: soma ponderada (pesos {@code length+1} ate 2) modulo 11; resto
     * menor que 2 vira 0, senao {@code 11 - resto}.
     */
    private static int checkDigit(String cpf, int length) {
        int sum = 0;
        int weight = length + 1;
        for (int i = 0; i < length; i++) {
            sum += (cpf.charAt(i) - '0') * weight--;
        }
        int mod = sum % 11;
        return mod < 2 ? 0 : 11 - mod;
    }
}
