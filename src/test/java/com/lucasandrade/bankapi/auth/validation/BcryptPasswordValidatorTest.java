package com.lucasandrade.bankapi.auth.validation;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o limite de tamanho da senha isoladamente (sem contexto Spring) e
 * documenta, com o proprio encoder, o comportamento do bcrypt que justifica ele.
 */
class BcryptPasswordValidatorTest {

    /** 'a' com acento: 2 bytes em UTF-8, entao 36 caracteres = 72 bytes. */
    private static final String ACCENTED = "á";

    @Test
    void acceptsPasswordUpToTheBcryptLimit() {
        assertThat(BcryptPasswordValidator.fitsInBcrypt("supersecret1")).isTrue();
        assertThat(BcryptPasswordValidator.fitsInBcrypt("a".repeat(BcryptPassword.MAX_BYTES))).isTrue();
    }

    @Test
    void rejectsPasswordPastTheBcryptLimit() {
        assertThat(BcryptPasswordValidator.fitsInBcrypt("a".repeat(BcryptPassword.MAX_BYTES + 1))).isFalse();
    }

    /**
     * O limite e em bytes, nao em caracteres: 36 caracteres acentuados ja ocupam
     * os 72 bytes, e o 37o seria descartado. Um {@code @Size(max = 72)} deixaria
     * essa senha passar e ser truncada.
     */
    @Test
    void countsBytesNotCharacters() {
        String exactlyAtLimit = ACCENTED.repeat(BcryptPassword.MAX_BYTES / 2);
        assertThat(exactlyAtLimit).hasSize(36);
        assertThat(BcryptPasswordValidator.fitsInBcrypt(exactlyAtLimit)).isTrue();
        assertThat(BcryptPasswordValidator.fitsInBcrypt(exactlyAtLimit + ACCENTED)).isFalse();
    }

    @Test
    void nullIsConsideredValid_leftToNotBlank() {
        assertThat(new BcryptPasswordValidator().isValid(null, null)).isTrue();
    }

    /**
     * Por que o limite existe: o bcrypt ignora tudo depois do 72o byte sem avisar,
     * entao duas senhas que so diferem no final produzem hashes equivalentes — a
     * senha "longa" e, na pratica, apenas o seu comeco. Este teste prova o
     * comportamento no encoder que a aplicacao usa; e ele que a validacao evita.
     */
    @Test
    void bcryptSilentlyIgnoresBytesPastTheLimit() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // custo minimo: teste rapido
        String atLimit = "a".repeat(BcryptPassword.MAX_BYTES);
        String longer = atLimit + "-final-que-o-bcrypt-descarta";

        String hashOfLonger = encoder.encode(longer);

        assertThat(encoder.matches(atLimit, hashOfLonger))
                .as("o pedaco de 72 bytes abre a conta criada com a senha longa")
                .isTrue();
    }
}
