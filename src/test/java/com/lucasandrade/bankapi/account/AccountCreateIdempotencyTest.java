package com.lucasandrade.bankapi.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotencia da criacao de conta via cabecalho {@code Idempotency-Key}.
 *
 * <p>Sem a chave, o retry apos timeout e indistinguivel de um CPF duplicado: o
 * cliente cria a conta, perde o 201 no timeout, reenvia — e recebe 422 "ja existe
 * conta" sem saber que a conta e dele. Com a chave, o reenvio devolve a resposta
 * original (201 com o MESMO id), e o 422 volta a significar so CPF de outra conta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class AccountCreateIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String createBody(String document) {
        return """
                { "ownerName": "Lucas Andrade", "document": "%s" }
                """.formatted(document);
    }

    @Test
    void create_replayWithSameKey_returnsOriginalAccount_insteadOfDuplicateError() throws Exception {
        String body = createBody("79369676996");

        String firstResponse = mockMvc.perform(post("/api/v1/accounts")
                        .header("Idempotency-Key", "create-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(firstResponse).get("id").asText();

        // retry apos timeout: MESMA chave, MESMO pedido -> devolve a conta original
        // (201 com o mesmo id), nao o 422 de documento duplicado
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Idempotency-Key", "create-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void create_sameKeyWithDifferentRequest_isRejectedAsKeyReuse() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Idempotency-Key", "create-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("81636957650")))
                .andExpect(status().isCreated());

        // mesma chave com outro documento nao e retry, e reuso indevido: 409
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Idempotency-Key", "create-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("19895886896")))
                .andExpect(status().isConflict());
    }

    @Test
    void create_failedAttemptDoesNotConsumeKey() throws Exception {
        // outra conta ja ocupa este CPF (criada sem chave)
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("66094864417")))
                .andExpect(status().isCreated());

        // tentativa com chave falha (422): o rollback desfaz tambem o registro da chave
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Idempotency-Key", "create-key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("66094864417")))
                .andExpect(status().isUnprocessableEntity());

        // a mesma chave continua utilizavel para o pedido corrigido
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Idempotency-Key", "create-key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("01490546294")))
                .andExpect(status().isCreated());
    }
}
