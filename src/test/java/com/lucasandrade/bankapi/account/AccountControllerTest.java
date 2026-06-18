package com.lucasandrade.bankapi.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
}
