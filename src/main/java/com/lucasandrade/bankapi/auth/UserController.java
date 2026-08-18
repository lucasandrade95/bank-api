package com.lucasandrade.bankapi.auth;

import com.lucasandrade.bankapi.auth.dto.ChangePasswordRequest;
import com.lucasandrade.bankapi.auth.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operacoes do usuario autenticado sobre a PROPRIA conta de acesso ({@code /me}).
 *
 * <p>Vive fora de {@code /api/v1/auth/**} de proposito: aquele prefixo e publico
 * por definicao (e onde se obtem o token) e esta liberado inteiro no
 * {@code SecurityConfig}. Colocar uma rota protegida la dentro dependeria de uma
 * excecao ordenada antes do {@code permitAll} — o tipo de regra que funciona hoje
 * e quebra em silencio na proxima edicao. Aqui a rota cai no
 * {@code anyRequest().authenticated()} sem excecao nenhuma, e o {@code /me} deixa
 * explicito que o alvo e sempre o dono do token, nunca um id vindo da URL.
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final AuthService service;

    public UserController(AuthService service) {
        this.service = service;
    }

    /**
     * Perfil do usuario autenticado ("quem sou eu?"): id, username e data de
     * cadastro. E o que um cliente chama logo apos o login para saber quem o
     * token representa — nada de senha, hash ou token na resposta.
     */
    @GetMapping
    public ResponseEntity<UserProfileResponse> me(Authentication authentication) {
        return ResponseEntity.ok(service.profile(authentication.getName()));
    }

    /**
     * Troca a senha do usuario autenticado. O usuario vem do token (subject),
     * nao do corpo — ninguem troca a senha de outro. Sucesso e <b>204</b>: nao
     * ha o que devolver (e nao se devolve token novo, o atual continua valido).
     */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(Authentication authentication,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
