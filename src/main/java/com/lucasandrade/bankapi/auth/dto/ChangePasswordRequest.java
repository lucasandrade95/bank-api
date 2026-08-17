package com.lucasandrade.bankapi.auth.dto;

import com.lucasandrade.bankapi.auth.validation.BcryptPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de troca de senha do usuario autenticado.
 *
 * <p>A senha atual e exigida de proposito, mesmo com o token JWT ja provando quem
 * e o usuario: um token roubado (ou uma sessao deixada aberta) nao pode virar
 * uma troca de senha que tranca o dono de verdade para fora. E a mesma
 * reautenticacao que todo banco pede antes de mexer em credencial.
 *
 * <p>A nova senha segue exatamente a politica do cadastro ({@code min 8} e o teto
 * em bytes do bcrypt, ver {@link BcryptPassword}) — a regra vive nas mesmas
 * anotacoes, entao nao ha como a troca aceitar uma senha que o cadastro recusa.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "currentPassword e obrigatorio")
        String currentPassword,

        @NotBlank(message = "newPassword e obrigatorio")
        @Size(min = 8, message = "newPassword deve ter no minimo 8 caracteres")
        @BcryptPassword(message = "newPassword deve ter no maximo 72 bytes (limite do bcrypt)")
        String newPassword
) {
}
