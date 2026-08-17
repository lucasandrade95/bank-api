package com.lucasandrade.bankapi.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Troca de senha do usuario autenticado ({@code PUT /api/v1/users/me/password}).
 *
 * <p>Os testes exercitam o fluxo completo pela API (cadastro -> troca -> login),
 * com token JWT de verdade em vez de {@code @WithMockUser}: o que se quer provar
 * e que a senha que passa a valer no login e a nova, e que a antiga deixa de valer.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserPasswordChangeTest {

    private static final String PASSWORD = "supersecret1";
    private static final String NEW_PASSWORD = "brandnewsecret2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void changePassword_returns204_newPasswordLogsIn_oldOneDoesNot() throws Exception {
        String token = register("pw_ok", PASSWORD);

        changePassword(token, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        login("pw_ok", NEW_PASSWORD).andExpect(status().isOk());
        login("pw_ok", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns422_andKeepsOldPassword() throws Exception {
        String token = register("pw_wrong_current", PASSWORD);

        changePassword(token, "not-the-current-one", NEW_PASSWORD)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messages[0]").value("Senha atual incorreta"));

        // Nada mudou: a senha antiga segue valendo e a nova nao entrou.
        login("pw_wrong_current", PASSWORD).andExpect(status().isOk());
        login("pw_wrong_current", NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_sameAsCurrent_returns422() throws Exception {
        String token = register("pw_same", PASSWORD);

        changePassword(token, PASSWORD, PASSWORD)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.messages[0]").value("Nova senha deve ser diferente da senha atual"));
    }

    @Test
    void changePassword_newPasswordBelowPolicy_returns400_andKeepsOldPassword() throws Exception {
        String token = register("pw_short", PASSWORD);

        changePassword(token, PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(containsString("newPassword deve ter no minimo 8")));

        login("pw_short", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changePassword_newPasswordPastBcryptLimit_returns400() throws Exception {
        String token = register("pw_bcrypt", PASSWORD);

        changePassword(token, PASSWORD, "a".repeat(73))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(containsString("limite do bcrypt")));
    }

    @Test
    void changePassword_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_onlyAffectsTheAuthenticatedUser() throws Exception {
        String tokenA = register("pw_user_a", PASSWORD);
        register("pw_user_b", PASSWORD);

        changePassword(tokenA, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        // O usuario vem do token: a troca de A nao encosta na senha de B.
        login("pw_user_b", PASSWORD).andExpect(status().isOk());
        login("pw_user_b", NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    private ResultActions changePassword(String token, String current, String next) throws Exception {
        return mockMvc.perform(put("/api/v1/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(current, next)));
    }

    private static String body(String current, String next) {
        return """
                { "currentPassword": "%s", "newPassword": "%s" }
                """.formatted(current, next);
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "username": "%s", "password": "%s" }
                        """.formatted(username, password)));
    }

    private String register(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "%s" }
                                """.formatted(username, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
