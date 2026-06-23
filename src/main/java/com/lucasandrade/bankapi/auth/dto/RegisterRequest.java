package com.lucasandrade.bankapi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload de cadastro de usuario. */
public record RegisterRequest(

        @NotBlank(message = "username e obrigatorio")
        @Size(min = 3, max = 60, message = "username deve ter entre 3 e 60 caracteres")
        String username,

        @NotBlank(message = "password e obrigatorio")
        @Size(min = 8, max = 100, message = "password deve ter no minimo 8 caracteres")
        String password
) {
}
