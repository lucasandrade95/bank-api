package com.lucasandrade.bankapi.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Payload de login (troca credenciais por um token JWT). */
public record LoginRequest(

        @NotBlank(message = "username e obrigatorio")
        String username,

        @NotBlank(message = "password e obrigatorio")
        String password
) {
}
