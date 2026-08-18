package com.lucasandrade.bankapi.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil do usuario autenticado ({@code GET /api/v1/users/me}).
 *
 * <p>Usa token JWT de verdade (cadastro pela API) em vez de {@code @WithMockUser}:
 * o que se quer provar e que o perfil devolvido e o do <b>dono do token</b>, e que
 * nada de credencial (senha, hash, token) sai na resposta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserProfileTest {

    private static final String PASSWORD = "supersecret1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository repository;

    @Test
    void me_returns200_withIdUsernameAndCreatedAt_ofTheTokenOwner() throws Exception {
        Instant before = Instant.now();
        String token = register("me_ok");

        String body = me(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("me_ok"))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        UUID id = UUID.fromString(json.get("id").asText());
        Instant createdAt = Instant.parse(json.get("createdAt").asText());

        // O id e o createdAt sao os da linha persistida, nao valores inventados na resposta.
        AppUser stored = repository.findByUsername("me_ok").orElseThrow();
        assertThat(id).isEqualTo(stored.getId());
        assertThat(createdAt).isEqualTo(stored.getCreatedAt());
        assertThat(createdAt).isAfterOrEqualTo(before.minusSeconds(1));
    }

    @Test
    void me_neverExposesPasswordHashOrAnyCredential() throws Exception {
        String token = register("me_no_secret");

        String body = me(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                // Contrato fechado: exatamente id, username e createdAt — um campo novo
                // na entidade nao entra aqui sem passar por este teste.
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andReturn().getResponse().getContentAsString();

        // Nem o hash nem a senha em texto puro aparecem em lugar nenhum do corpo.
        String hash = repository.findByUsername("me_no_secret").orElseThrow().getPasswordHash();
        assertThat(body).doesNotContain(hash).doesNotContain(PASSWORD);
    }

    @Test
    void me_returnsTheProfileOfTheTokenOwner_notOfAnotherUser() throws Exception {
        String tokenA = register("me_user_a");
        String tokenB = register("me_user_b");

        me(tokenA).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("me_user_a"));
        me(tokenB).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("me_user_b"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token));
    }

    private String register(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "%s" }
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
