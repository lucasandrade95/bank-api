package com.lucasandrade.bankapi.shared;

import com.lucasandrade.bankapi.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobertura das bordas do tratamento de erro: os casos que NAO passam por um
 * handler especifico.
 *
 * <p>O ponto e que nenhuma falha escapa do corpo de erro padrao ({@link ApiError})
 * e que uma falha inesperada do servidor volta como 500 generico — nunca como um
 * 4xx (que culparia o cliente por um defeito nosso) nem com a mensagem interna da
 * excecao no corpo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class GlobalExceptionHandlerTest {

    /** Detalhe interno que jamais pode aparecer na resposta. */
    private static final String INTERNAL_DETAIL = "constraint fk_accounts_owner on table accounts";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Espiao do repositorio: forcar uma falha aqui simula um defeito inesperado no
     * meio do processamento (banco fora do ar, bug numa camada abaixo) — algo que
     * nenhum handler especifico preve.
     */
    @SpyBean
    private AccountRepository repository;

    /**
     * Falha inesperada (um defeito, nao um erro do cliente): 500 no corpo padrao,
     * com mensagem fixa e generica. Antes de existir a rede de seguranca, um
     * {@code IllegalArgumentException} vindo de qualquer canto virava 400 — a API
     * culpava o cliente por um bug do servidor.
     */
    @Test
    void unexpectedFailure_returns500_inStandardErrorBody() throws Exception {
        doThrow(new IllegalArgumentException(INTERNAL_DETAIL))
                .when(repository).findById(any(UUID.class));

        mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.messages[0]").value("Erro interno inesperado"));
    }

    /** A mensagem interna da excecao fica no log do servidor, nunca na resposta. */
    @Test
    void unexpectedFailure_doesNotLeakInternalDetail() throws Exception {
        doThrow(new IllegalStateException(INTERNAL_DETAIL))
                .when(repository).findById(any(UUID.class));

        mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("fk_accounts_owner"))));
    }

    /**
     * Metodo HTTP nao suportado numa rota existente: o Spring levanta um
     * {@code ErrorResponse} com status 405. O status e preservado (nao vira 500) e
     * o corpo sai no formato padrao da API.
     */
    @Test
    void unsupportedMethod_returns405_inStandardErrorBody() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.messages").isArray());
    }

    /** Rota inexistente continua 404 — a rede de seguranca de 500 nao a engole. */
    @Test
    void unknownRoute_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/rota-que-nao-existe"))
                .andExpect(status().isNotFound());
    }
}
