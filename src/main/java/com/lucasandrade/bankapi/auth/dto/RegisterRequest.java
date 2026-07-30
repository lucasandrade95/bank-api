package com.lucasandrade.bankapi.auth.dto;

import com.lucasandrade.bankapi.auth.validation.BcryptPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload de cadastro de usuario. */
public record RegisterRequest(

        @NotBlank(message = "username e obrigatorio")
        @Size(min = 3, max = 60, message = "username deve ter entre 3 e 60 caracteres")
        String username,

        /**
         * Senha em texto puro (guardada apenas como hash BCrypt). O maximo nao e um
         * {@code @Size} em caracteres: quem impoe o teto e o bcrypt, que conta
         * <b>bytes</b> e descarta o excedente em silencio — ver {@link BcryptPassword}.
         */
        @NotBlank(message = "password e obrigatorio")
        @Size(min = 8, message = "password deve ter no minimo 8 caracteres")
        @BcryptPassword
        String password
) {
}
