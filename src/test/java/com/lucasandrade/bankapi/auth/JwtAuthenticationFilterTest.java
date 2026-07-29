package com.lucasandrade.bankapi.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O filtro de JWT roda ANTES do {@code ExceptionTranslationFilter} do Spring
 * Security, entao o que ele deixa escapar nao vira 401: escapa da cadeia de
 * seguranca inteira e cai no tratamento de erro do container, virando 500 com um
 * corpo que nao e o {@code ApiError} padrao da API.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationFilterTest {

    private static final String ANY_ACCOUNT = "00000000-0000-0000-0000-000000000000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Token assinado por nos, valido e nao expirado, mas cujo subject nao tem mais
     * cadastro (usuario removido enquanto o token ainda estava na validade). A
     * credencial nao identifica mais ninguem: e um 401, nao uma falha do servidor.
     */
    @Test
    void validTokenForUnknownUser_returns401_withStandardErrorBody() throws Exception {
        String token = jwtService.generateToken("usuario-sem-cadastro");

        mockMvc.perform(get("/api/v1/accounts/{id}", ANY_ACCOUNT)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.messages").isArray());
    }

    /** {@code Bearer} sem token: entra no ramo de autenticacao, mas nao ha o que validar. */
    @Test
    void blankBearerToken_returns401_withStandardErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", ANY_ACCOUNT)
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void garbageToken_returns401_withStandardErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", ANY_ACCOUNT)
                        .header("Authorization", "Bearer nao-e-um-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    /**
     * O contraponto: o filtro NAO pode engolir tudo. Uma falha de infraestrutura ao
     * carregar o usuario (banco fora do ar) nao e credencial invalida — engoli-la
     * responderia 401 "seu token nao presta" para um problema nosso, mandando o
     * cliente trocar um token que estava correto. Ela sobe e vira 500.
     *
     * <p>Vai direto no filtro, sem MockMvc, porque so assim da para forcar a falha
     * de infraestrutura sem derrubar o banco de teste.
     */
    @Test
    void infrastructureFailure_isNotSwallowedAs401() {
        AppUserDetailsService brokenUserLookup = new AppUserDetailsService(null) {
            @Override
            public UserDetails loadUserByUsername(String username) {
                throw new DataAccessResourceFailureException("banco fora do ar");
            }
        };
        String token = jwtService.generateToken("lucas");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer " + token);

        assertThatThrownBy(() -> new JwtAuthenticationFilter(jwtService, brokenUserLookup)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
