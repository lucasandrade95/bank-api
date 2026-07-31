package com.lucasandrade.bankapi.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teto do saldo: um credito que nao caberia na coluna monetaria
 * ({@code NUMERIC(19,2)}) e recusado como regra de negocio (422), e nao deixado
 * estourar no banco — onde virava um 409 falando de concorrencia, causa que nada
 * tem a ver com o problema.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class AccountBalanceCeilingTest {

    /** Maior saldo representavel em NUMERIC(19,2): 17 digitos inteiros + centavos. */
    private static final String MAX = "99999999999999999.99";

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
    void depositUpToTheCeilingIsAccepted_beyondItReturns422() throws Exception {
        String id = createAccount("90010010092");

        // o teto em si cabe: a regra so recusa o que a coluna nao guarda
        deposit(id, MAX);

        // mais um centavo nao cabe. Antes isto era um 409 "conflito de concorrencia"
        // vindo da violacao de integridade no flush; agora e regra de negocio,
        // informando quanto ainda cabe (aqui, zero).
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 0.01 }"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.messages[0]")
                        .value("Saldo maximo da conta excedido; credito maximo aceito: 0.00"));

        // a operacao recusada nao mexeu no saldo
        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"balance\":" + MAX)));
    }

    @Test
    void depositBeyondTheCeiling_reportsHowMuchStillFits() throws Exception {
        String id = createAccount("90010010173");
        deposit(id, "99999999999999900.00");

        // sobram 99.99 de capacidade; pedir 100.00 estoura e a mensagem diz o quanto cabe
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messages[0]")
                        .value("Saldo maximo da conta excedido; credito maximo aceito: 99.99"));

        // e o valor informado passa
        deposit(id, "99.99");
        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(content().string(containsString("\"balance\":" + MAX)));
    }

    @Test
    void transferIntoAFullAccount_returns422_andRollsBack() throws Exception {
        String source = createAccount("90010010254");
        String destination = createAccount("90010010335");
        deposit(source, "10.00");
        deposit(destination, MAX);

        // o credito no destino nao cabe: a transferencia inteira falha
        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "destinationAccountId": "%s", "amount": 10.00 }
                                """.formatted(destination)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messages[0]")
                        .value("Saldo maximo da conta excedido; credito maximo aceito: 0.00"));

        // rollback atomico: a origem nao foi debitada
        mockMvc.perform(get("/api/v1/accounts/{id}", source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10.00));
    }
}
