package com.lucasandrade.bankapi.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.RequiredSearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void health_isPublic_andReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void metrics_requireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void metrics_exposeCustomBankOperationsMeter() throws Exception {
        mockMvc.perform(get("/actuator/metrics/bank.account.operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("bank.account.operations"));
    }

    @Test
    @WithMockUser
    void deposit_incrementsBusinessCounter() throws Exception {
        double before = depositCount();

        String accountId = createAccount("10120230364");
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 25.00 }"))
                .andExpect(status().isOk());

        assertThat(depositCount()).isEqualTo(before + 1);
    }

    private double depositCount() {
        RequiredSearch search = meterRegistry.get("bank.account.operations").tag("type", "deposit");
        return search.counter().count();
    }

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
}
