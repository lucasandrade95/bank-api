package com.lucasandrade.bankapi.auth;

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
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String username, String password) throws Exception {
        String body = """
                { "username": "%s", "password": "%s" }
                """.formatted(username, password);

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        return node.get("token").asText();
    }

    @Test
    void register_returns201_withToken() throws Exception {
        register("alice", "supersecret1");
    }

    @Test
    void register_duplicateUsername_returns422() throws Exception {
        register("bob", "supersecret1");

        String body = """
                { "username": "bob", "password": "anothersecret1" }
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        String body = """
                { "username": "carol", "password": "short" }
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        register("dave", "supersecret1");

        String body = """
                { "username": "dave", "password": "supersecret1" }
                """;
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        register("erin", "supersecret1");

        String body = """
                { "username": "erin", "password": "wrongpassword" }
                """;
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        String body = """
                { "ownerName": "Frank", "document": "88990011035" }
                """;
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidToken_returns201() throws Exception {
        String token = register("grace", "supersecret1");

        String body = """
                { "ownerName": "Grace", "document": "99001122027" }
                """;
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", "00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void health_isPublic_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/health"))
                .andExpect(status().isOk());
    }
}
