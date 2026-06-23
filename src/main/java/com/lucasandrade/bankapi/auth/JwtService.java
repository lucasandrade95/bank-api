package com.lucasandrade.bankapi.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * Emite e valida tokens JWT (HS256). O segredo e o tempo de expiracao vem da
 * configuracao, nunca hardcoded no codigo.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    /** Gera um token assinado cujo subject e o username. */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expiration.toMillis());
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(key)
                .compact();
    }

    /**
     * Valida a assinatura e a expiracao do token e devolve o username (subject).
     * Lanca {@link io.jsonwebtoken.JwtException} se o token for invalido.
     */
    public String extractUsername(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
