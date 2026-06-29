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

/**
 * Da a cada requisicao um identificador de correlacao (request id).
 *
 * <p>Le o cabecalho {@code X-Request-Id} enviado pelo cliente/gateway e, se vier
 * ausente ou em branco, gera um UUID. O valor e colocado no {@link MDC} do SLF4J
 * (chave {@code requestId}) para aparecer em todas as linhas de log da requisicao
 * — assim e possivel correlacionar logs espalhados de uma mesma chamada — e
 * devolvido no mesmo cabecalho da resposta, para o cliente referenciar a chamada
 * ao abrir um chamado de suporte.
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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
