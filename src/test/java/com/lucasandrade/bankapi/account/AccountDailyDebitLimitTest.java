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
 * Limite diario de debito: o teto do que pode SAIR de uma conta num mesmo dia,
 * somando saque e transferencia enviada.
 *
 * <p>O limite e baixado para 200.00 nesta classe ({@code properties}) para os
 * testes estourarem o teto com valores pequenos, em vez de dependerem do valor
 * configurado por padrao.
 */
@SpringBootTest(properties = "bank.limits.daily-debit=200.00")
@AutoConfigureMockMvc
@WithMockUser
class AccountDailyDebitLimitTest {

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

    private void deposit(String id, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": %s }".formatted(amount)))
                .andExpect(status().isOk());
    }

    @Test
    void withdrawals_accumulateAgainstTheDailyLimit() throws Exception {
        String id = createAccount("80010010084");
        deposit(id, "1000.00");

        // primeiro saque cabe no limite de 200
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 150.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(850.00));

        // o segundo estoura o que sobrou (50): 422 no corpo de erro padrao,
        // informando quanto ainda ha disponivel hoje
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 60.00 }"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.messages[0]")
                        .value("Limite diario de debito excedido; disponivel hoje: 50.00"));

        // a operacao recusada nao mexeu no saldo nem deixou lancamento
        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(850.00));

        // gastar exatamente o que sobrou continua permitido
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 50.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(800.00));
    }

    @Test
    void transfer_consumesTheSameLimitAsWithdrawal_andRollsBackWhenExceeded() throws Exception {
        String source = createAccount("80010010165");
        String destination = createAccount("80010010246");
        deposit(source, "1000.00");

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 150.00 }"))
                .andExpect(status().isOk());

        // saque + transferencia dividem o mesmo teto: sobrou 50, entao 100 estoura
        String transfer = """
                { "destinationAccountId": "%s", "amount": %s }
                """;
        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer.formatted(destination, "100.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messages[0]")
                        .value("Limite diario de debito excedido; disponivel hoje: 50.00"));

        // rollback atomico: o destino nao recebeu nada
        mockMvc.perform(get("/api/v1/accounts/{id}", destination))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.00));

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer.formatted(destination, "50.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.balance").value(800.00))
                .andExpect(jsonPath("$.destination.balance").value(50.00));
    }

    @Test
    void creditsDoNotConsumeTheLimit() throws Exception {
        String source = createAccount("80010010327");
        String destination = createAccount("80010010408");

        // deposito nao tira dinheiro da conta: passa muito acima do limite de 200
        deposit(source, "5000.00");
        deposit(source, "5000.00");
        mockMvc.perform(get("/api/v1/accounts/{id}", source))
                .andExpect(jsonPath("$.balance").value(10000.00));

        // a transferencia gasta TODO o limite da origem...
        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "destinationAccountId": "%s", "amount": 200.00 }
                                """.formatted(destination)))
                .andExpect(status().isOk());

        // ...e nada do limite do destino: receber nao e debito, entao ele ainda
        // pode sacar os proprios 200 do dia
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", destination)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 200.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.00));
    }
}
