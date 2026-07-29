package com.lucasandrade.bankapi.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Le o cabecalho {@code Authorization: Bearer <token>}, valida o JWT e, se valido,
 * popula o SecurityContext. Tokens ausentes ou invalidos apenas seguem sem
 * autenticar — quem decide barrar e a cadeia de autorizacao do Spring Security.
 *
 * <p><b>Por que o tratamento de erro aqui e delicado:</b> este filtro roda ANTES do
 * {@code ExceptionTranslationFilter} do Spring Security (ele e registrado antes do
 * {@code UsernamePasswordAuthenticationFilter}, e o tradutor vem depois na cadeia).
 * Excecao que escapa daqui nao vira 401: escapa da cadeia de seguranca inteira e cai
 * no tratamento de erro do container, virando <b>500</b> com um corpo que nem passa
 * pelo {@code GlobalExceptionHandler} — fora do {@link com.lucasandrade.bankapi.shared.ApiError}
 * padrao da API. Por isso todo motivo conhecido para "esta credencial nao vale" e
 * tratado aqui dentro, seguindo sem autenticar.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length());
            // "Bearer" sem token: nao ha o que validar. Sem esta guarda o parser do
            // JWT recusaria a string vazia com IllegalArgumentException, que nao e
            // JwtException e escaparia do filtro como 500.
            if (!token.isBlank()) {
                authenticate(request, token);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Autentica a requisicao a partir do token, ou segue sem autenticar quando ele
     * nao identifica um usuario valido — nos dois casos a cadeia decide o resto.
     *
     * <p>Os dois motivos tratados sao "esta credencial nao vale":
     * <ul>
     *   <li>{@link JwtException} — token malformado, com assinatura invalida ou expirado;</li>
     *   <li>{@link UsernameNotFoundException} — token nosso, assinado e dentro da validade,
     *       mas cujo subject nao tem mais cadastro (usuario removido enquanto o token
     *       ainda valia). Um token nao deixa de ser apresentavel so porque o usuario
     *       sumiu, entao este caso acontece em producao — e sem este catch escapava
     *       do filtro e virava 500 numa situacao que e claramente 401.</li>
     * </ul>
     *
     * <p>O catch e <b>propositalmente restrito</b> a esses dois tipos. Engolir qualquer
     * excecao transformaria uma falha nossa (banco fora do ar ao carregar o usuario,
     * por exemplo) em 401 "seu token nao presta": o cliente sairia trocando uma
     * credencial que estava correta e a falha real ficaria invisivel. Uma falha de
     * infraestrutura precisa continuar subindo e sendo 500.
     */
    private void authenticate(HttpServletRequest request, String token) {
        try {
            String username = jwtService.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | UsernameNotFoundException ex) {
            // credencial invalida: segue sem autenticar -> 401 na cadeia
            SecurityContextHolder.clearContext();
        }
    }
}
