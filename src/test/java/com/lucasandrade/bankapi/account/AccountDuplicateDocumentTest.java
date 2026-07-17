package com.lucasandrade.bankapi.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unicidade do documento (CPF) na criacao de conta.
 *
 * <p>A checagem previa do service e um check-then-act e nao fecha a corrida: quem
 * perde chega ao INSERT e e barrado pela restricao UNIQUE do banco. Estes testes
 * fixam que os DOIS caminhos — o detectado na checagem e o pego pelo banco —
 * devolvem a mesma resposta ao cliente (422 com a mesma mensagem).
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class AccountDuplicateDocumentTest {

    private static final String DUPLICATE_MESSAGE = "Ja existe conta para o documento informado";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Espiao do repositorio: permite forcar a checagem previa a dizer "nao existe"
     * e assim exercitar o caminho de quem PERDE a corrida, que em producao so
     * acontece com duas requisicoes concorrentes.
     */
    @SpyBean
    private AccountRepository repository;

    // O H2 em memoria e compartilhado entre as classes de teste, entao cada CPF aqui
    // e exclusivo desta classe: reusar um de outro teste colidiria com o indice unico.
    private String createAccountRequest(String document) {
        return """
                { "ownerName": "Lucas Andrade", "document": "%s" }
                """.formatted(document);
    }

    @Test
    void duplicateDocument_caughtByPreCheck_returns422() throws Exception {
        String document = "81818181800";

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAccountRequest(document)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAccountRequest(document)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messages[0]").value(DUPLICATE_MESSAGE));
    }

    /**
     * Simula quem perde a corrida: com a checagem previa cega (sempre "nao existe"),
     * a segunda criacao chega ao INSERT e viola a restricao UNIQUE. O cliente deve
     * receber o mesmo 422 do caminho acima — e nao o 409 generico de integridade,
     * que falaria de Idempotency-Key e nada tem a ver com CPF repetido.
     */
    @Test
    void duplicateDocument_racingPastPreCheck_stillReturns422() throws Exception {
        String document = "73737373744";
        doReturn(false).when(repository).existsByDocument(anyString());

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAccountRequest(document)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAccountRequest(document)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messages[0]").value(DUPLICATE_MESSAGE));
    }
}
