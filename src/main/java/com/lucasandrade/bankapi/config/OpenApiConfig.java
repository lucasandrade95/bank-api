package com.lucasandrade.bankapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documento OpenAPI da API — o contrato legivel por maquina que o Swagger UI e
 * qualquer gerador de cliente consomem.
 *
 * <p>O esquema de seguranca faz parte desse contrato: as rotas de conta exigem
 * {@code Authorization: Bearer <token>} desde a autenticacao JWT, mas o documento
 * nao dizia isso. O efeito pratico e que o Swagger UI nao oferecia o botao
 * <b>Authorize</b> (todo "Try it out" numa rota protegida voltava 401 sem haver
 * onde colar o token) e um cliente gerado a partir do contrato nao saberia que
 * precisa enviar o cabecalho. A exigencia e declarada como requisito
 * <b>global</b> — proteger e a regra, publico e a excecao — e as poucas rotas
 * publicas (cadastro/login, health) a removem localmente com
 * {@code @SecurityRequirements} vazio, espelhando o {@code SecurityConfig}.
 */
@Configuration
public class OpenApiConfig {

    /** Nome do esquema de seguranca, referenciado pelo requisito global. */
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI bankApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bank API")
                        .version("0.1.0")
                        .description("API REST de operacoes bancarias (contas e transacoes) em Java + Spring Boot"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT obtido em /api/v1/auth/register ou /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
