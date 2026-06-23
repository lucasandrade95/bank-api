package com.lucasandrade.bankapi.account;

import com.fasterxml.jackson.databind.JsonNode;
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

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser // operacoes de conta exigem autenticacao; testes rodam como usuario logado
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Cria uma conta e devolve o id gerado, para uso em testes de operacao. */
    private String createAccount(String document) throws Exception {
        String body = """
                { "ownerName": "Lucas Andrade", "document": "%s" }
                """.formatted(document);

        String response = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        return node.get("id").asText();
    }

    @Test
    void createAccount_returns201_andZeroBalance() throws Exception {
        String body = """
                { "ownerName": "Lucas Andrade", "document": "12345678901" }
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ownerName").value("Lucas Andrade"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void createAccount_invalidDocument_returns400() throws Exception {
        String body = """
                { "ownerName": "Fulano", "document": "abc" }
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAccount_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deposit_increasesBalance_returns200() throws Exception {
        String id = createAccount("11111111111");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 150.75 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.75));
    }

    @Test
    void withdraw_withinBalance_returns200_andDebits() throws Exception {
        String id = createAccount("22222222222");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 40.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(60.00));
    }

    @Test
    void withdraw_insufficientBalance_returns422() throws Exception {
        String id = createAccount("33333333333");

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00 }"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deposit_negativeAmount_returns400() throws Exception {
        String id = createAccount("44444444444");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": -5.00 }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deposit_accountNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", "00000000-0000-0000-0000-000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00 }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_movesFundsAtomically_returns200() throws Exception {
        String source = createAccount("55555555555");
        String destination = createAccount("66666666666");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        String body = """
                { "destinationAccountId": "%s", "amount": 30.00 }
                """.formatted(destination);

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.balance").value(70.00))
                .andExpect(jsonPath("$.destination.balance").value(30.00))
                .andExpect(jsonPath("$.amount").value(30.00));
    }

    @Test
    void transfer_insufficientBalance_returns422_andRollsBack() throws Exception {
        String source = createAccount("77777777777");
        String destination = createAccount("88888888888");

        String body = """
                { "destinationAccountId": "%s", "amount": 50.00 }
                """.formatted(destination);

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());

        // rollback: nenhuma conta foi creditada
        mockMvc.perform(get("/api/v1/accounts/{id}", destination))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void transfer_toSameAccount_returns422() throws Exception {
        String id = createAccount("99999999999");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        String body = """
                { "destinationAccountId": "%s", "amount": 10.00 }
                """.formatted(id);

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transfer_destinationNotFound_returns404() throws Exception {
        String source = createAccount("10101010101");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        String body = """
                { "destinationAccountId": "00000000-0000-0000-0000-000000000000", "amount": 10.00 }
                """;

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_negativeAmount_returns400() throws Exception {
        String source = createAccount("12121212121");
        String destination = createAccount("13131313131");

        String body = """
                { "destinationAccountId": "%s", "amount": -10.00 }
                """.formatted(destination);

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statement_newAccount_returnsEmptyList() throws Exception {
        String id = createAccount("14141414141");

        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void statement_recordsDepositAndWithdrawal_mostRecentFirst() throws Exception {
        String id = createAccount("15151515151");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 30.00 }"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // mais recente primeiro: o saque
                .andExpect(jsonPath("$[0].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$[0].amount").value(30.00))
                .andExpect(jsonPath("$[0].balanceAfter").value(70.00))
                .andExpect(jsonPath("$[1].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[1].amount").value(100.00))
                .andExpect(jsonPath("$[1].balanceAfter").value(100.00));
    }

    @Test
    void statement_transfer_recordsBothLegsWithCounterparty() throws Exception {
        String source = createAccount("16161616161");
        String destination = createAccount("17171717171");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        String body = """
                { "destinationAccountId": "%s", "amount": 40.00 }
                """.formatted(destination);

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // perna de saida na conta origem, apontando para o destino
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$[0].amount").value(40.00))
                .andExpect(jsonPath("$[0].balanceAfter").value(60.00))
                .andExpect(jsonPath("$[0].counterpartyAccountId").value(destination));

        // perna de entrada na conta destino, apontando para a origem
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", destination))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$[0].amount").value(40.00))
                .andExpect(jsonPath("$[0].balanceAfter").value(40.00))
                .andExpect(jsonPath("$[0].counterpartyAccountId").value(source));
    }

    @Test
    void statement_failedTransfer_recordsNothing() throws Exception {
        String source = createAccount("18181818181");
        String destination = createAccount("19191919191");

        String body = """
                { "destinationAccountId": "%s", "amount": 50.00 }
                """.formatted(destination);

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());

        // rollback: nenhum lancamento registrado em nenhuma das contas
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", destination))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void statement_accountNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
