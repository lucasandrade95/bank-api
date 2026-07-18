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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                // saldo normalizado para 2 casas decimais (centavos), nunca "0" cru
                .andExpect(jsonPath("$.balance").value(0.00));
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
    void createAccount_startsActive() throws Exception {
        String id = createAccount("70010020039");

        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void block_freezesAccount_rejectsDepositWith422() throws Exception {
        String id = createAccount("70010020110");

        mockMvc.perform(post("/api/v1/accounts/{id}/block", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        // conta congelada nao movimenta: deposito cai em 422 (regra de negocio)
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00 }"))
                .andExpect(status().isUnprocessableEntity());

        // saque tambem e barrado enquanto bloqueada
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00 }"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unblock_reactivatesAccount_allowsOperationsAgain() throws Exception {
        String id = createAccount("70010020209");

        mockMvc.perform(post("/api/v1/accounts/{id}/block", id))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/accounts/{id}/unblock", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // reativada, volta a aceitar deposito
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 25.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(25.00));
    }

    @Test
    void transfer_toBlockedDestination_returns422_andRollsBack() throws Exception {
        String source = createAccount("70010020381");
        String destination = createAccount("70010020462");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        // congela o destino: o credito da transferencia deve ser barrado
        mockMvc.perform(post("/api/v1/accounts/{id}/block", destination))
                .andExpect(status().isOk());

        String body = """
                { "destinationAccountId": "%s", "amount": 30.00 }
                """.formatted(destination);

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());

        // rollback: a origem nao foi debitada
        mockMvc.perform(get("/api/v1/accounts/{id}", source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void block_accountNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/block", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void close_zeroBalance_returns200_andRejectsFurtherOperations() throws Exception {
        String id = createAccount("70010050027");

        mockMvc.perform(post("/api/v1/accounts/{id}/close", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // conta encerrada nao movimenta: deposito cai em 422 (regra de negocio)
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.00 }"))
                .andExpect(status().isUnprocessableEntity());

        // encerrar de novo e idempotente: continua CLOSED sem erro
        mockMvc.perform(post("/api/v1/accounts/{id}/close", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void close_withBalance_returns422_andKeepsAccountActive() throws Exception {
        String id = createAccount("70010060090");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 50.00 }"))
                .andExpect(status().isOk());

        // conta com saldo diferente de zero nao pode ser encerrada
        mockMvc.perform(post("/api/v1/accounts/{id}/close", id))
                .andExpect(status().isUnprocessableEntity());

        // segue ativa e operando normalmente
        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void close_isTerminal_rejectsBlockAndUnblockWith422() throws Exception {
        String id = createAccount("70010070052");

        mockMvc.perform(post("/api/v1/accounts/{id}/close", id))
                .andExpect(status().isOk());

        // estado terminal: uma conta encerrada nao volta a (des)bloquear
        mockMvc.perform(post("/api/v1/accounts/{id}/block", id))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/accounts/{id}/unblock", id))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void close_accountNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/close", "00000000-0000-0000-0000-000000000000"))
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
                .andExpect(jsonPath("$.balance").value(0.00));
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
    void statement_filtersByType() throws Exception {
        String id = createAccount("65432198746");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 30.00 }"))
                .andExpect(status().isOk());

        // so os depositos: 1 lancamento, e nao o saque
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("type", "DEPOSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[0].amount").value(100.00));

        // so os saques: 1 lancamento
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("type", "WITHDRAWAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"));

        // tipo sem lancamentos nesta conta: extrato vazio
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("type", "TRANSFER_IN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));

        // sem filtro: os dois lancamentos aparecem
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void statement_invalidType_returns400() throws Exception {
        String id = createAccount("98712345628");

        // tipo inexistente cai no corpo de erro padrao (type mismatch do enum)
        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id)
                        .param("type", "NAO_EXISTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void statementSummary_totalsInOutAndNet_withBreakdownByType() throws Exception {
        String id = createAccount("13087803030");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 50.00 }"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 30.00 }"))
                .andExpect(status().isOk());

        // 2 depositos (150) - 1 saque (30) => entra 150, sai 30, liquido 120
        mockMvc.perform(get("/api/v1/accounts/{id}/statement/summary", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.totalIn").value(150.00))
                .andExpect(jsonPath("$.totalOut").value(30.00))
                .andExpect(jsonPath("$.net").value(120.00))
                .andExpect(jsonPath("$.byType.DEPOSIT.count").value(2))
                .andExpect(jsonPath("$.byType.DEPOSIT.total").value(150.00))
                .andExpect(jsonPath("$.byType.WITHDRAWAL.count").value(1))
                .andExpect(jsonPath("$.byType.WITHDRAWAL.total").value(30.00))
                // tipos sem lancamento no periodo nao aparecem no detalhamento
                .andExpect(jsonPath("$.byType.TRANSFER_IN").doesNotExist());
    }

    @Test
    void statementSummary_newAccount_returnsZeroedTotals() throws Exception {
        String id = createAccount("59043495050");

        mockMvc.perform(get("/api/v1/accounts/{id}/statement/summary", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalIn").value(0))
                .andExpect(jsonPath("$.totalOut").value(0))
                .andExpect(jsonPath("$.net").value(0))
                .andExpect(jsonPath("$.byType").isEmpty())
                // mesmo com periodo vazio, os totais saem na escala monetaria canonica
                // (2 casas) — "0.00", nao "0"; jsonPath coage o numero e esconderia isso,
                // entao conferimos a forma serializada crua.
                .andExpect(content().string(containsString("\"totalIn\":0.00")))
                .andExpect(content().string(containsString("\"totalOut\":0.00")))
                .andExpect(content().string(containsString("\"net\":0.00")));
    }

    @Test
    void statementSummary_filtersByPeriod() throws Exception {
        String id = createAccount("62550780000");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        // janela abrangente: soma o deposito
        mockMvc.perform(get("/api/v1/accounts/{id}/statement/summary", id)
                        .param("from", "1900-01-01")
                        .param("to", "2999-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.totalIn").value(100.00));

        // janela inteiramente no futuro: nada entra na soma
        mockMvc.perform(get("/api/v1/accounts/{id}/statement/summary", id)
                        .param("from", "2999-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalIn").value(0));
    }

    @Test
    void statementSummary_invalidDateRange_returns400() throws Exception {
        String id = createAccount("47188077002");

        mockMvc.perform(get("/api/v1/accounts/{id}/statement/summary", id)
                        .param("from", "2026-02-01")
                        .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void statementSummary_accountNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/statement/summary",
                        "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAccounts_returnsPaginatedEnvelope_containingCreatedAccount() throws Exception {
        String doc = "11122233396";
        createAccount(doc);

        mockMvc.perform(get("/api/v1/accounts").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.totalPages").exists())
                .andExpect(jsonPath("$.last").exists())
                // a conta recem-criada aparece na listagem; filtro por documento
                // para ser robusto ao estado do H2 compartilhado entre testes
                .andExpect(jsonPath("$.content[?(@.document=='" + doc + "')]").isNotEmpty());
    }

    @Test
    void listAccounts_pagination_respectsSize() throws Exception {
        createAccount("22233344405");
        createAccount("33344455508");

        // ha pelo menos duas contas; size=1 => a pagina 0 nao e a ultima
        mockMvc.perform(get("/api/v1/accounts")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void listAccounts_filtersByStatus() throws Exception {
        String active = createAccount("81472936582");
        String blocked = createAccount("39261847013");

        // congela uma das contas para diferencia-la pela situacao
        mockMvc.perform(post("/api/v1/accounts/{id}/block", blocked))
                .andExpect(status().isOk());

        // filtro BLOCKED: traz a conta congelada, nunca a ativa
        mockMvc.perform(get("/api/v1/accounts")
                        .param("size", "100")
                        .param("status", "BLOCKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + blocked + "')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id=='" + active + "')]").isEmpty())
                // toda conta devolvida no filtro esta de fato BLOCKED
                .andExpect(jsonPath("$.content[?(@.status!='BLOCKED')]").isEmpty());

        // filtro ACTIVE: traz a conta ativa, nunca a congelada
        mockMvc.perform(get("/api/v1/accounts")
                        .param("size", "100")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + active + "')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id=='" + blocked + "')]").isEmpty());

        // sem filtro: as duas contas aparecem
        mockMvc.perform(get("/api/v1/accounts").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + active + "')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id=='" + blocked + "')]").isNotEmpty());
    }

    @Test
    void listAccounts_invalidStatus_returns400() throws Exception {
        // situacao inexistente cai no corpo de erro padrao (type mismatch do enum)
        mockMvc.perform(get("/api/v1/accounts").param("status", "NAO_EXISTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void listAccounts_invalidPagination_returns400() throws Exception {
        // size acima do maximo permitido (100)
        mockMvc.perform(get("/api/v1/accounts").param("size", "101"))
                .andExpect(status().isBadRequest());
        // size minimo e 1
        mockMvc.perform(get("/api/v1/accounts").param("size", "0"))
                .andExpect(status().isBadRequest());
        // page nao pode ser negativa
        mockMvc.perform(get("/api/v1/accounts").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    /** Deposita um valor e devolve o id do lancamento gerado (via extrato). */
    private String depositAndGetTransactionId(String accountId, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": " + amount + " }"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/v1/accounts/{id}/statement", accountId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("content").get(0).get("id").asText();
    }

    @Test
    void transaction_returnsSingleReceipt() throws Exception {
        String id = createAccount("15350946056");
        String txId = depositAndGetTransactionId(id, "100.00");

        // comprovante de um unico lancamento pelo id
        mockMvc.perform(get("/api/v1/accounts/{id}/statement/{txId}", id, txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(txId))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.balanceAfter").value(100.00));
    }

    @Test
    void transaction_notFound_returns404() throws Exception {
        String id = createAccount("94848209056");

        // conta existe, mas o lancamento nao
        mockMvc.perform(get("/api/v1/accounts/{id}/statement/{txId}", id,
                        "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void transaction_fromAnotherAccount_returns404_notLeaked() throws Exception {
        String owner = createAccount("40056231075");
        String other = createAccount("70768686016");
        String txId = depositAndGetTransactionId(owner, "50.00");

        // o lancamento existe, mas e de outra conta: escopo por conta => 404,
        // nunca expõe o comprovante alheio
        mockMvc.perform(get("/api/v1/accounts/{id}/statement/{txId}", other, txId))
                .andExpect(status().isNotFound());
    }

    @Test
    void transaction_accountNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/statement/{txId}",
                        "00000000-0000-0000-0000-000000000000",
                        "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound());
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

    @Test
    void deposit_fractionalScale_isNormalizedToTwoDecimals() throws Exception {
        String id = createAccount("11122233477");

        // deposito com 1 casa decimal (10.5) => saldo devolvido com 2 casas (10.50)
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.5 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10.50))
                // prova a escala exata na serializacao: "10.50", nunca "10.5"
                .andExpect(content().string(containsString("\"balance\":10.50")));

        // mais um deposito de 10.5: 21.00, nunca "21.0"
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.5 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(21.00))
                .andExpect(content().string(containsString("\"balance\":21.00")));
    }

    @Test
    void statement_normalizesTransactionAmountAndBalance() throws Exception {
        String id = createAccount("11122233558");

        // deposito de 10.5 => o lancamento no extrato tambem sai normalizado
        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 10.5 }"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/{id}/statement", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(10.50))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(10.50))
                .andExpect(content().string(containsString("\"amount\":10.50")))
                .andExpect(content().string(containsString("\"balanceAfter\":10.50")));
    }

    @Test
    void transfer_fractionalScale_isNormalizedInResponse() throws Exception {
        String source = createAccount("11122233639");
        String destination = createAccount("11122233710");

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 100.00 }"))
                .andExpect(status().isOk());

        String body = """
                { "destinationAccountId": "%s", "amount": 20.1 }
                """.formatted(destination);

        // valor movido e saldos resultantes saem com 2 casas (20.10 / 79.90)
        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(20.10))
                .andExpect(jsonPath("$.source.balance").value(79.90))
                .andExpect(jsonPath("$.destination.balance").value(20.10))
                .andExpect(content().string(containsString("\"amount\":20.10")));
    }
}
