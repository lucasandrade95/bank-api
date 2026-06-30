package com.lucasandrade.bankapi.account.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Valida que uma string e um CPF estruturalmente valido (11 digitos cujos dois
 * digitos verificadores conferem pelo algoritmo modulo 11).
 *
 * <p>Vai alem de checar o formato {@code \d{11}}: rejeita numeros de tamanho
 * certo mas matematicamente impossiveis (ex.: {@code 12345678901}) e os de
 * digitos repetidos (ex.: {@code 00000000000}), que um banco real nunca aceita.
 *
 * <p>Aceita {@code null} (a obrigatoriedade fica a cargo de {@code @NotBlank}).
 */
@Documented
@Constraint(validatedBy = CpfValidator.class)
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
public @interface Cpf {

    String message() default "document deve ser um CPF valido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
