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
                { "ownerName": "Lucas Andrade", "document": "11144477735" }
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
    void createAccount_cpfWithWrongCheckDigit_returns400() throws Exception {
        // 11 digitos, formato valido, mas digitos verificadores nao conferem:
        // a validacao @Cpf deve reprovar (um banco real nao aceitaria este CPF).
        String body = """
                { "ownerName": "Fulano", "document": "12345678901" }
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    void createAccount_malformedJson_returns400_withStandardErrorBody() throws Exception {
        // JSON quebrado: deve cair no handler de HttpMessageNotReadableException
        // e devolver o corpo de erro padrao (ApiError), nao o do Spring.
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"ownerName\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages.length()").value(1));
    }

    @Test
    void getAccount_idNotUuid_returns400_withStandardErrorBody() throws Exception {
        // id de caminho que nao e UUID: handler de MethodArgumentTypeMismatchException
        mockMvc.perform(get("/api/v1/accounts/{id}", "nao-e-um-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages[0]").value("id: valor invalido"));
    }

    @Test
    void getAccount_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deposit_increasesBalance_returns200() throws Exception {
        String id = createAccount("22255588846");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 150.75 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.75));
    }

    @Test
    void withdraw_withinBalance_returns200_andDebits() throws Exception {
        String id = createAccount("33366699957");

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
        String id = createAccount("12345678062");

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00 }"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deposit_negativeAmount_returns400() throws Exception {
        String id = createAccount("98765432029");

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
        String source = createAccount("10020030088");
        String destination = createAccount("45678912011");

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
        String source = createAccount("23456781008");
        String destination = createAccount("34567891066");

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
        String id = createAccount("56789012060");

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
        String source = createAccount("67890123035");

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
        String source = createAccount("78901234009");
        String destination = createAccount("89012345057");

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
        String id = createAccount("90123456002");

        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void statement_recordsDepositAndWithdrawal_mostRecentFirst() throws Exception {
        String id = createAccount("11223344002");

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
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                // mais recente primeiro: o saque
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.content[0].amount").value(30.00))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(70.00))
                .andExpect(jsonPath("$.content[1].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[1].amount").value(100.00))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(100.00));
    }

    @Test
    void statement_transfer_recordsBothLegsWithCounterparty() throws Exception {
        String source = createAccount("22334455032");
        String destination = createAccount("33445566062");

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
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.content[0].amount").value(40.00))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(60.00))
                .andExpect(jsonPath("$.content[0].counterpartyAccountId").value(destination));

        // perna de entrada na conta destino, apontando para a origem
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", destination))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.content[0].amount").value(40.00))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(40.00))
                .andExpect(jsonPath("$.content[0].counterpartyAccountId").value(source));
    }

    @Test
    void statement_failedTransfer_recordsNothing() throws Exception {
        String source = createAccount("44556677092");
        String destination = createAccount("55667788012");

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
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", destination))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void statement_accountNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statement_pagination_respectsPageAndSize() throws Exception {
        String id = createAccount("66778899042");

        // tres lancamentos (depositos) para paginar em paginas de 2
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"amount\": 10.00 }"))
                    .andExpect(status().isOk());
        }

        // primeira pagina: 2 itens, ainda nao e a ultima
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));

        // segunda pagina: o item restante, agora e a ultima
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void statement_dateRange_filtersByPeriod() throws Exception {
        String id = createAccount("32165498791");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00 }"))
                .andExpect(status().isOk());

        // janela abrangente (from inclusivo, to inclusivo): pega o lancamento
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("from", "1900-01-01")
                        .param("to", "2999-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));

        // janela inteiramente no futuro: nao pega nada
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("from", "2999-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        // janela inteiramente no passado (to exclusivo no dia seguinte): nao pega nada
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("to", "1900-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void statement_invalidDateRange_returns400() throws Exception {
        String id = createAccount("45678912364");

        // from depois de to
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("from", "2026-02-01")
                        .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // data malformada cai no corpo de erro padrao (type mismatch)
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("from", "nao-e-data"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void statement_invalidSize_returns400() throws Exception {
        String id = createAccount("77889900007");

        // size acima do maximo permitido (100)
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());

        // size minimo e 1
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("size", "0"))
                .andExpect(status().isBadRequest());

        // page nao pode ser negativa
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }
}
