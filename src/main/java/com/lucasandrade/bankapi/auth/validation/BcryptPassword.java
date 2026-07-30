package com.lucasandrade.bankapi.auth.validation;

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
 * Valida que uma senha cabe inteira no algoritmo que vai proteger a conta: o
 * bcrypt so considera os primeiros {@value #MAX_BYTES} <b>bytes</b> UTF-8 da
 * senha e <b>descarta o resto em silencio</b>, sem erro nem aviso.
 *
 * <p>Aceitar uma senha maior que isso significa guardar o hash de um pedaco dela.
 * O efeito e uma promessa quebrada sem ninguem perceber: quem escolhe uma
 * frase-senha longa esta protegido apenas pelo comeco dela, e duas senhas que
 * compartilham os {@value #MAX_BYTES} primeiros bytes sao <b>a mesma credencial</b>
 * — trocar so o final nao troca a senha, e a "antiga" continua entrando.
 *
 * <p>O limite e contado em bytes, nao em caracteres, porque e assim que o bcrypt
 * conta: um {@code @Size(max = 72)} pareceria resolver e nao resolveria, ja que
 * 36 caracteres acentuados ({@code 'a'} com acento ocupa 2 bytes em UTF-8) ou 18
 * emojis (4 bytes cada) ja preenchem o limite — a senha seria truncada com menos
 * da metade dos caracteres permitidos.
 *
 * <p>A escolha e <b>recusar</b> a senha longa, nao adapta-la. As alternativas
 * usuais — pre-hashear a senha (ex.: SHA-256 em base64) antes do bcrypt, ou
 * trocar para Argon2/scrypt, que nao tem esse teto — mudam o formato do hash
 * guardado e exigiriam migrar as senhas existentes. Recusar na borda custa uma
 * regra de validacao, deixa o contrato explicito para o cliente (400 com o
 * motivo) e nao inventa uma senha diferente da que o usuario digitou.
 *
 * <p>Aceita {@code null} (a obrigatoriedade fica a cargo de {@code @NotBlank}).
 */
@Documented
@Constraint(validatedBy = BcryptPasswordValidator.class)
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
public @interface BcryptPassword {

    /**
     * Tamanho maximo, em bytes UTF-8, que o bcrypt leva em conta. Nao e um numero
     * escolhido por nos: e o limite do algoritmo (a chave do Blowfish tem 56
     * bytes, e a implementacao aceita 72 antes de descartar o excedente).
     */
    int MAX_BYTES = 72;

    String message() default "password deve ter no maximo 72 bytes (limite do bcrypt)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
