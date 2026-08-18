package com.lucasandrade.bankapi.auth.dto;

import com.lucasandrade.bankapi.auth.AppUser;

import java.time.Instant;
import java.util.UUID;

/**
 * Perfil do usuario autenticado, como devolvido por {@code GET /api/v1/users/me}.
 *
 * <p>E um record separado da entidade de proposito: {@link AppUser} carrega o
 * {@code passwordHash}, e a unica forma de garantir que ele <b>nunca</b> sai pela
 * API e nao existir um campo para ele aqui — nao depende de {@code @JsonIgnore}
 * nem de alguem lembrar de nao serializar a entidade.
 */
public record UserProfileResponse(
        UUID id,
        String username,
        Instant createdAt
) {
    public static UserProfileResponse from(AppUser user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getCreatedAt());
    }
}
