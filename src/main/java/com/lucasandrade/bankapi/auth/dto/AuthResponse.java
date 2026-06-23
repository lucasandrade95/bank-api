package com.lucasandrade.bankapi.auth.dto;

/** Resposta de autenticacao: o token JWT e metadados para o cliente. */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds
) {
    public static AuthResponse bearer(String token, long expiresInSeconds) {
        return new AuthResponse(token, "Bearer", expiresInSeconds);
    }
}
