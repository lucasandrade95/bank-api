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
 * Consulta dos limites da conta ({@code GET /accounts/{id}/limits}): o cliente
 * pergunta quanto ainda pode movimentar hoje ANTES de tentar a operacao, em vez
 * de descobrir o limite estourando-o (422).
 *
 * <p>O limite e baixado para 200.00 ({@code properties}) pelo mesmo motivo dos
 * testes de enforcement: exercitar o teto com valores pequenos.
 */
@SpringBootTest(properties = "bank.limits.daily-debit=200.00")
@AutoConfigureMockMvc
@WithMockUser
class AccountLimitsTest {

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
    void freshAccount_hasTheWholeLimitAvailable() throws Exception {
        String id = createAccount("81020030062");

        mockMvc.perform(get("/api/v1/accounts/{id}/limits", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyDebit.limit").value(200.00))
                .andExpect(jsonPath("$.dailyDebit.usedToday").value(0.00))
                .andExpect(jsonPath("$.dailyDebit.availableToday").value(200.00));
    }

    @Test
    void debitsConsumeTheReportedLimit_andCreditsDoNot() throws Exception {
        String id = createAccount("81020030143");
        String destination = createAccount("81020030224");
        deposit(id, "1000.00");

        // deposito e credito: nao consome nada do limite
        mockMvc.perform(get("/api/v1/accounts/{id}/limits", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyDebit.usedToday").value(0.00))
                .andExpect(jsonPath("$.dailyDebit.availableToday").value(200.00));

        // saque e transferencia enviada somam no mesmo teto: 120 + 50 = 170
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 120.00 }"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "destinationAccountId": "%s", "amount": 50.00 }
                                """.formatted(destination)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/{id}/limits", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyDebit.limit").value(200.00))
                .andExpect(jsonPath("$.dailyDebit.usedToday").value(170.00))
                .andExpect(jsonPath("$.dailyDebit.availableToday").value(30.00));

        // quem recebeu a transferencia nao debitou nada: limite inteiro de pe
        mockMvc.perform(get("/api/v1/accounts/{id}/limits", destination))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyDebit.usedToday").value(0.00))
                .andExpect(jsonPath("$.dailyDebit.availableToday").value(200.00));
    }

    @Test
    void reportedAvailable_isExactlyWhatTheNextOperationObeys() throws Exception {
        String id = createAccount("81020030305");
        deposit(id, "1000.00");

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 150.00 }"))
                .andExpect(status().isOk());

        // a consulta diz que sobraram 50...
        mockMvc.perform(get("/api/v1/accounts/{id}/limits", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyDebit.availableToday").value(50.00));

        // ...e o enforcement concorda: 50.01 estoura citando os mesmos 50
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 50.01 }"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messages[0]")
                        .value("Limite diario de debito excedido; disponivel hoje: 50.00"));

        // sacar exatamente o informado passa e zera o disponivel
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 50.00 }"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/accounts/{id}/limits", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyDebit.usedToday").value(200.00))
                .andExpect(jsonPath("$.dailyDebit.availableToday").value(0.00));
    }

    @Test
    void blockedAccount_canStillSeeItsLimits() throws Exception {
        // consultar limite e leitura, como o extrato: conta congelada nao movimenta,
        // mas o titular continua enxergando a propria situacao
        String id = createAccount("81020030496");
        mockMvc.perform(post("/api/v1/accounts/{id}/block", id))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/{id}/limits", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyDebit.availableToday").value(200.00));
    }

    @Test
    void unknownAccount_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/limits",
                        "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
