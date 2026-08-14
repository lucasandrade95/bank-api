package com.lucasandrade.bankapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O documento OpenAPI e o contrato legivel por maquina da API — estes testes
 * afirmam que a parte de SEGURANCA desse contrato bate com o SecurityConfig:
 * bearer JWT exigido por padrao, com as rotas publicas explicitamente isentas.
 *
 * <p>Sem o esquema declarado, o Swagger UI nao oferece o botao Authorize e um
 * cliente gerado a partir do contrato nao sabe que precisa enviar o token.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    /** A documentacao e publica: e onde um integrador comeca, antes de ter token. */
    @Test
    void apiDocs_arePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void apiDocs_declareBearerJwtSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    /**
     * O requisito e GLOBAL (proteger e a regra): uma operacao protegida nao
     * repete o requisito localmente — ela herda o do documento. Assim uma rota
     * nova nasce documentada como protegida sem ninguem lembrar de anotar.
     */
    @Test
    void apiDocs_requireBearerGloballyAndProtectedOperationsInheritIt() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{id}/deposit'].post.security").doesNotExist());
    }

    /**
     * As rotas publicas removem o requisito com um {@code security: []} local
     * (o @SecurityRequirements vazio): cadastro/login — onde o token e obtido,
     * exigir token ali seria circular — e o health de probe.
     *
     * <p>{@code hasSize(0)} e nao {@code isEmpty()} de proposito: o isEmpty do
     * MockMvc tambem passa quando o campo NEM EXISTE — que aqui seria o bug
     * (sem o security local, a operacao herda o requisito global).
     */
    @Test
    void apiDocs_markPublicOperationsWithEmptySecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.security", hasSize(0)))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security", hasSize(0)))
                .andExpect(jsonPath("$.paths['/api/v1/accounts/health'].get.security", hasSize(0)));
    }
}
