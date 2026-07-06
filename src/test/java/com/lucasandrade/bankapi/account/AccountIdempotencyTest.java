package com.lucasandrade.bankapi.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotencia das operacoes com dinheiro via cabecalho {@code Idempotency-Key}.
 * Uma repeticao da mesma requisicao (tipico num retry apos timeout) devolve a
 * resposta original sem reexecutar o efeito colateral — nao dobra o deposito,
 * saque ou transferencia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class AccountIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createAccount(String document) throws Exception {
        String body = """
                { "ownerName": "Lucas Andrade", "document": "%s" }
                """.formatted(document);

        String response = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    @Test
    void deposit_replayWithSameKey_returnsOriginalResult_withoutDoubleCharging() throws Exception {
        String id = createAccount("52910192989");

        // primeiro deposito com a chave: saldo vira 100
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .header("Idempotency-Key", "dep-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));

        // repeticao com a MESMA chave: devolve a resposta original (saldo 100),
        // sem creditar de novo
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .header("Idempotency-Key", "dep-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));

        // o saldo real confirma: um unico deposito foi aplicado
        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void deposit_differentKeys_processIndependently() throws Exception {
        String id = createAccount("31847261507");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .header("Idempotency-Key", "dep-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));

        // chave diferente = operacao nova: soma
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .header("Idempotency-Key", "dep-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.00));
    }

    @Test
    void deposit_withoutKey_isNotIdempotent() throws Exception {
        String id = createAccount("74002518302");

        // sem cabecalho, a idempotencia fica desligada (retro-compativel): duas
        // requisicoes iguais somam
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"amount\": 50.00 }"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void transfer_replayWithSameKey_returnsCachedResult_withoutMovingFundsTwice() throws Exception {
        String source = createAccount("60291837450");
        String destination = createAccount("81526304970");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        String body = """
                { "destinationAccountId": "%s", "amount": 30.00 }
                """.formatted(destination);

        // primeira transferencia com a chave
        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .header("Idempotency-Key", "trf-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.balance").value(70.00))
                .andExpect(jsonPath("$.destination.balance").value(30.00));

        // replay: mesma resposta, sem mover dinheiro de novo
        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .header("Idempotency-Key", "trf-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.balance").value(70.00))
                .andExpect(jsonPath("$.destination.balance").value(30.00));

        // saldos reais confirmam uma unica transferencia
        mockMvc.perform(get("/api/v1/accounts/{id}", source))
                .andExpect(jsonPath("$.balance").value(70.00));
        mockMvc.perform(get("/api/v1/accounts/{id}", destination))
                .andExpect(jsonPath("$.balance").value(30.00));
    }

    @Test
    void failedOperation_doesNotConsumeKey_soRetryCanSucceed() throws Exception {
        String id = createAccount("47382915050");

        // saque sem saldo falha (422) — a chave nao deve ser gravada
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .header("Idempotency-Key", "wd-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00 }"))
                .andExpect(status().isUnprocessableEntity());

        // agora ha saldo
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        // reusar a mesma chave apos a falha executa de verdade (nao ficou memoizada)
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .header("Idempotency-Key", "wd-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(90.00));
    }
}
