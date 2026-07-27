package com.lucasandrade.bankapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unicidade do username no cadastro de usuario.
 *
 * <p>A checagem previa do service e um check-then-act e nao fecha a corrida: quem
 * perde chega ao INSERT e e barrado pela restricao UNIQUE do banco. Estes testes
 * fixam que os DOIS caminhos — o detectado na checagem e o pego pelo banco —
 * devolvem a mesma resposta ao cliente (422 com a mesma mensagem). Mesmo contrato
 * ja garantido para o documento duplicado em {@code AccountDuplicateDocumentTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthDuplicateUsernameTest {

    private static final String DUPLICATE_MESSAGE = "Username ja esta em uso";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Espiao do repositorio: permite forcar a checagem previa a dizer "nao existe"
     * e assim exercitar o caminho de quem PERDE a corrida, que em producao so
     * acontece com dois cadastros concorrentes.
     */
    @SpyBean
    private AppUserRepository repository;

    // O H2 em memoria e compartilhado entre as classes de teste, entao cada username
    // aqui e exclusivo desta classe: reusar um de outro teste colidiria com o indice unico.
    private String registerRequest(String username) {
        return """
                { "username": "%s", "password": "supersecret1" }
                """.formatted(username);
    }

    @Test
    void duplicateUsername_caughtByPreCheck_returns422() throws Exception {
        String username = "duplicate-precheck";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest(username)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest(username)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messages[0]").value(DUPLICATE_MESSAGE));
    }

    /**
     * Simula quem perde a corrida: com a checagem previa cega (sempre "nao existe"),
     * o segundo cadastro chega ao INSERT e viola a restricao UNIQUE. O cliente deve
     * receber o mesmo 422 do caminho acima — e nao o 409 generico de integridade,
     * que mandaria esperar e repetir uma tentativa que nunca vai dar certo.
     */
    @Test
    void duplicateUsername_racingPastPreCheck_stillReturns422() throws Exception {
        String username = "duplicate-race";
        doReturn(false).when(repository).existsByUsername(anyString());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest(username)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest(username)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messages[0]").value(DUPLICATE_MESSAGE));
    }
}
