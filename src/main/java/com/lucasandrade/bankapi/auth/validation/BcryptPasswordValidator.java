package com.lucasandrade.bankapi.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * Implementa a validacao de {@link BcryptPassword}: mede a senha em bytes UTF-8
 * (a unidade que o bcrypt usa) e recusa a que nao caberia inteira no algoritmo.
 */
public class BcryptPasswordValidator implements ConstraintValidator<BcryptPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // ausencia/obrigatoriedade fica com @NotBlank
        }
        return fitsInBcrypt(value);
    }

    /** {@code true} se a senha inteira e considerada pelo bcrypt (nao e truncada). */
    static boolean fitsInBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= BcryptPassword.MAX_BYTES;
    }
}
