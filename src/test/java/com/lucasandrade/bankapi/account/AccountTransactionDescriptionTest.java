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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Descricao livre e opcional nas operacoes com dinheiro ({@code description}):
 * o cliente diz "para que" foi o lancamento e o extrato mostra isso de volta.
 * O que importa aqui e o contrato — opcional, limitada, normalizada, presente nas
 * duas pernas da transferencia e parte da identidade do pedido para a idempotencia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class AccountTransactionDescriptionTest {

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

    private JsonNode statement(String id) throws Exception {
        String response = mockMvc.perform(get("/api/v1/accounts/{id}/statement", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("content");
    }

    @Test
    void depositWithDescription_showsItOnTheStatement_andOnTheReceipt() throws Exception {
        String id = createAccount("90010020055");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 100.00, "description": "salario de agosto" }
                                """))
                .andExpect(status().isOk());

        JsonNode lines = statement(id);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).get("description").asText()).isEqualTo("salario de agosto");

        // o comprovante do lancamento traz a mesma descricao
        String transactionId = lines.get(0).get("id").asText();
        mockMvc.perform(get("/api/v1/accounts/{id}/statement/{txId}", id, transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("salario de agosto"));
    }

    @Test
    void descriptionIsOptional_absentComesBackAsNull() throws Exception {
        String id = createAccount("90010020136");

        // payload de sempre, sem o campo: continua valido
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 50.00 }"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void blankDescription_isStoredAsAbsent_andSurroundingSpacesAreTrimmed() throws Exception {
        String id = createAccount("90010020217");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00, \"description\": \"   \" }"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 5.00, \"description\": \"  aluguel  \" }"))
                .andExpect(status().isOk());

        JsonNode lines = statement(id); // mais recente primeiro
        assertThat(lines.get(0).get("type").asText()).isEqualTo("WITHDRAWAL");
        assertThat(lines.get(0).get("description").asText()).isEqualTo("aluguel");
        assertThat(lines.get(1).get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(lines.get(1).get("description").isNull()).isTrue();
    }

    @Test
    void transferDescription_appearsOnBothLegs() throws Exception {
        String source = createAccount("90010020306");
        String destination = createAccount("90010020489");
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "destinationAccountId": "%s", "amount": 30.00, "description": "racha do almoco" }
                                """.formatted(destination)))
                .andExpect(status().isOk());

        JsonNode out = statement(source).get(0);
        assertThat(out.get("type").asText()).isEqualTo("TRANSFER_OUT");
        assertThat(out.get("description").asText()).isEqualTo("racha do almoco");

        JsonNode in = statement(destination).get(0);
        assertThat(in.get("type").asText()).isEqualTo("TRANSFER_IN");
        assertThat(in.get("description").asText()).isEqualTo("racha do almoco");
    }

    @Test
    void descriptionLongerThanTheLimit_returns400_andRecordsNothing() throws Exception {
        String id = createAccount("90010020560");
        String tooLong = "x".repeat(Transaction.DESCRIPTION_MAX_LENGTH + 1);

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00, \"description\": \"%s\" }".formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(containsString("description deve ter no maximo 140 caracteres")));

        // rejeitado na borda: nem saldo nem extrato mudam
        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.00));
        assertThat(statement(id)).isEmpty();
    }

    @Test
    void descriptionAtExactlyTheLimit_isAccepted() throws Exception {
        String id = createAccount("90010020640");
        String maxed = "y".repeat(Transaction.DESCRIPTION_MAX_LENGTH);

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00, \"description\": \"%s\" }".formatted(maxed)))
                .andExpect(status().isOk());

        assertThat(statement(id).get(0).get("description").asText()).isEqualTo(maxed);
    }

    /**
     * A descricao faz parte da identidade do pedido: a mesma chave com outra
     * descricao NAO e um retry — e outro lancamento —, entao volta 409 em vez de
     * devolver a resposta guardada como se fosse a mesma operacao.
     */
    @Test
    void sameIdempotencyKey_withDifferentDescription_returns409() throws Exception {
        String id = createAccount("90010020721");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .header("Idempotency-Key", "desc-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00, \"description\": \"aluguel\" }"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .header("Idempotency-Key", "desc-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00, \"description\": \"condominio\" }"))
                .andExpect(status().isConflict());

        // e um retry com a mesma descricao (mesmo que com espacos nas pontas) e o
        // mesmo pedido: resposta guardada, sem segundo lancamento
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .header("Idempotency-Key", "desc-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00, \"description\": \" aluguel \" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10.00));

        assertThat(statement(id)).hasSize(1);
    }
}
