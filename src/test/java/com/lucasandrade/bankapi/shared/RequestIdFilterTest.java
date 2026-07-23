package com.lucasandrade.bankapi.shared;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestIdFilterTest {

    private static final String HEADER = "X-Request-Id";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatesRequestId_whenClientSendsNone() throws Exception {
        // rota publica (nao exige token): o filtro roda antes da seguranca
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HEADER))
                // UUID gerado pelo filtro
                .andExpect(header().string(HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void echoesClientRequestId_whenProvided() throws Exception {
        mockMvc.perform(get("/actuator/health").header(HEADER, "trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, "trace-123"));
    }

    @Test
    void echoesClientRequestId_whenUuid() throws Exception {
        String id = "3f2a1b8c-9d4e-4f10-8a7b-2c5d6e7f8091";

        mockMvc.perform(get("/actuator/health").header(HEADER, id))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, id));
    }

    /**
     * O id do cliente e reimpresso em toda linha de log da requisicao: um valor de
     * varios KB inflaria o volume de log da chamada inteira. Acima do limite o
     * filtro descarta e gera o proprio id.
     */
    @Test
    void generatesRequestId_whenClientIdIsTooLong() throws Exception {
        String tooLong = "a".repeat(RequestIdFilter.MAX_LENGTH + 1);

        mockMvc.perform(get("/actuator/health").header(HEADER, tooLong))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, matchesPattern(UUID_PATTERN)));
    }

    /** No limite exato o id ainda e aceito — a fronteira e inclusiva. */
    @Test
    void echoesClientRequestId_atMaxLength() throws Exception {
        String atLimit = "a".repeat(RequestIdFilter.MAX_LENGTH);

        mockMvc.perform(get("/actuator/health").header(HEADER, atLimit))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, atLimit));
    }

    /**
     * Log forging: um id com quebra de linha plantaria uma linha de log falsa,
     * corrompendo a trilha que o request id existe para tornar confiavel.
     *
     * <p>Vai direto no filtro, sem MockMvc, de proposito: uma quebra de linha em
     * cabecalho ja e barrada antes de chegar aqui (o {@code StrictHttpFirewall} do
     * Spring Security recusa caracteres de controle, e o proprio container HTTP
     * trata CR/LF como fim de cabecalho). Este teste cobre o filtro como
     * <b>defesa em profundidade</b>: se um dia essa protecao de fora for afrouxada,
     * o id ainda nao chega ao log.
     */
    @Test
    void generatesRequestId_whenClientIdHasLineBreak() throws Exception {
        String forged = "abc\n2026-07-23 ERROR linha falsa plantada pelo cliente";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader(HEADER, forged);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestIdFilter().doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HEADER))
                .isNotEqualTo(forged)
                .matches(UUID_PATTERN);
    }

    /** O MDC e limpo ao final para o id nao vazar para a proxima requisicao da thread. */
    @Test
    void clearsMdc_afterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader(HEADER, "trace-123");

        new RequestIdFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesRequestId_whenClientIdHasUnsafeCharacters() throws Exception {
        mockMvc.perform(get("/actuator/health").header(HEADER, "id com espaco e <script>"))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void generatesRequestId_whenClientIdIsBlank() throws Exception {
        mockMvc.perform(get("/actuator/health").header(HEADER, "   "))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, matchesPattern(UUID_PATTERN)));
    }
}
