package com.lucasandrade.bankapi.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Da a cada requisicao um identificador de correlacao (request id).
 *
 * <p>Le o cabecalho {@code X-Request-Id} enviado pelo cliente/gateway e, se vier
 * ausente ou fora do formato aceito, gera um UUID. O valor e colocado no
 * {@link MDC} do SLF4J (chave {@code requestId}) para aparecer em todas as linhas
 * de log da requisicao — assim e possivel correlacionar logs espalhados de uma
 * mesma chamada — e devolvido no mesmo cabecalho da resposta, para o cliente
 * referenciar a chamada ao abrir um chamado de suporte.
 *
 * <p>Roda antes de tudo (inclusive da seguranca) para que ate erros de
 * autenticacao carreguem o id, e sempre limpa o MDC ao final para nao vazar o
 * valor para a proxima requisicao que reutilize a thread do pool.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String MDC_KEY = "requestId";

    /** Tamanho maximo aceito para um id vindo do cliente. Cobre UUID (36) e ULID (26). */
    static final int MAX_LENGTH = 64;

    /**
     * Formato aceito para um id vindo do cliente: caracteres seguros para log e
     * para cabecalho HTTP. Cobre os formatos usuais de correlacao (UUID, ULID,
     * hex do {@code traceparent} do W3C, ids com prefixo de servico) sem aceitar
     * espaco, controle ou pontuacao que atrapalhe a leitura de uma linha de log.
     */
    private static final Pattern ACCEPTED_FORMAT =
            Pattern.compile("[A-Za-z0-9._:-]{1," + MAX_LENGTH + "}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Devolve o id mandado pelo cliente quando ele e aceitavel, ou um UUID novo
     * quando esta ausente ou fora do formato.
     *
     * <p>O valor e <b>controlado pelo cliente</b> e vai para dois lugares perigosos:
     * o {@link MDC}, entao ele e reimpresso em <b>toda linha de log</b> da requisicao,
     * e o cabecalho da resposta. Sem limite de tamanho, um cliente manda um id de
     * varios KB (ate o teto de cabecalho do container) e cada linha de log da chamada
     * carrega esse peso — um jeito barato de inflar o volume de log de quem opera a
     * API. Sem limite de caracteres, o id vira <b>log forging</b>: um valor com
     * separadores e capaz de imitar o formato das nossas linhas de log e plantar uma
     * linha falsa, corrompendo justamente a trilha que o request id existe para
     * tornar confiavel.
     *
     * <p>Um id fora do formato <b>nao</b> derruba a requisicao com 400: o request id
     * e um recurso de diagnostico, nao parte do contrato de negocio, e recusar uma
     * transferencia valida por causa de um cabecalho de observabilidade mal formado
     * seria trocar um problema pequeno por um grande. A degradacao e silenciosa e
     * segura — geramos um id proprio, a chamada segue normal e o cliente ve no
     * cabecalho da resposta que o id devolvido nao foi o que ele mandou.
     */
    private static String sanitize(String clientRequestId) {
        if (clientRequestId == null || !ACCEPTED_FORMAT.matcher(clientRequestId).matches()) {
            return UUID.randomUUID().toString();
        }
        return clientRequestId;
    }
}
