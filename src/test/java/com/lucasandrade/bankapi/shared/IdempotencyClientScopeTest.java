package com.lucasandrade.bankapi.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A {@code Idempotency-Key} vale dentro do <b>escopo do cliente</b> que a enviou,
 * nao num namespace global.
 *
 * <p>A chave e uma string escolhida pelo cliente ("1", "pagamento-do-mes"), entao
 * dois clientes escolherem a mesma nao e improvavel — e nao significa que um esteja
 * repetindo a requisicao do outro. Num namespace unico a chave de um decidia o
 * destino da requisicao do outro, com dois desfechos ruins que os testes abaixo
 * fixam: um 409 numa requisicao que o cliente nunca tinha enviado, e — pior — a
 * resposta guardada de outra pessoa devolvida com 200, fazendo a operacao sumir
 * sem erro.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyClientScopeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createAccount(String username, String document) throws Exception {
        String body = """
                { "ownerName": "Lucas Andrade", "document": "%s" }
                """.formatted(document);

        String response = mockMvc.perform(post("/api/v1/accounts")
                        .with(user(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    private MockHttpServletRequestBuilder deposit(String username, String accountId,
                                                  String key, String amount) {
        return post("/api/v1/accounts/{id}/deposit", accountId)
                .with(user(username))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"amount\": %s }".formatted(amount));
    }

    /**
     * Requisicoes diferentes (contas diferentes) sob a mesma chave: antes do escopo,
     * a impressao digital do segundo cliente nao batia com a do primeiro e ele levava
     * 409 sem nunca ter usado aquela chave.
     */
    @Test
    void sameKeyFromDifferentClients_onDifferentRequests_bothSucceed() throws Exception {
        String aliceAccount = createAccount("alice", "10765432196");
        String bobAccount = createAccount("bob", "11530864259");

        mockMvc.perform(deposit("alice", aliceAccount, "chave-comum-1", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));

        mockMvc.perform(deposit("bob", bobAccount, "chave-comum-1", "250.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(250.00));
    }

    /**
     * O caso grave: requisicoes <b>identicas</b> (mesma conta, mesmo valor) de dois
     * clientes sob a mesma chave. A impressao digital bate, entao num namespace
     * global o segundo cliente receberia 200 com a resposta do primeiro e o deposito
     * dele nunca aconteceria — dinheiro que some sem erro nenhum. Com o escopo, os
     * dois depositos sao aplicados: saldo 300, nao 150.
     */
    @Test
    void sameKeyFromDifferentClients_onIdenticalRequests_doesNotReplayTheOtherClientsResponse()
            throws Exception {
        String account = createAccount("alice", "12296296378");

        mockMvc.perform(deposit("alice", account, "chave-comum-2", "150.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.00));

        mockMvc.perform(deposit("bob", account, "chave-comum-2", "150.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(300.00));
    }

    /** O escopo nao afrouxa a idempotencia: o retry do MESMO cliente segue memoizado. */
    @Test
    void sameKeyFromSameClient_onSameRequest_stillReplaysWithoutDoubleCharging() throws Exception {
        String account = createAccount("alice", "13061728457");

        mockMvc.perform(deposit("alice", account, "chave-comum-3", "70.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(70.00));

        mockMvc.perform(deposit("alice", account, "chave-comum-3", "70.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(70.00));
    }

    /** E o reuso da chave pelo PROPRIO cliente em outra requisicao continua 409. */
    @Test
    void sameKeyFromSameClient_onDifferentRequest_stillConflicts() throws Exception {
        String account = createAccount("alice", "13827160529");

        mockMvc.perform(deposit("alice", account, "chave-comum-4", "10.00"))
                .andExpect(status().isOk());

        mockMvc.perform(deposit("alice", account, "chave-comum-4", "999.00"))
                .andExpect(status().isConflict());
    }
}
