package com.lucasandrade.bankapi.shared;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Identifica o <b>cliente</b> por tras da requisicao em curso — o dono do
 * namespace das {@code Idempotency-Key}.
 *
 * <p>Uma {@code Idempotency-Key} e um nome que o <i>cliente</i> da a uma
 * requisicao dele, nao um identificador global: dois clientes que escolhem a
 * string {@code "1"} nao estao repetindo a requisicao um do outro. Por isso a
 * chave so tem sentido dentro do escopo de quem a enviou — ver
 * {@link IdempotencyService}.
 *
 * <p>O escopo e o <b>subject do JWT</b> (o username autenticado), guardado como
 * hash SHA-256: a tabela de idempotencia precisa apenas responder "foi o mesmo
 * cliente?", nunca dizer quem ele e, entao ela nao carrega uma copia do cadastro
 * de usuarios e a coluna fica de largura fixa, sem depender do tamanho do
 * username. Mesma escolha ja feita para a impressao digital da requisicao.
 *
 * <p>Sem autenticacao o escopo e {@link #ANONYMOUS}. Na pratica isso nao acontece
 * pela API — toda rota com dinheiro exige token —, mas o valor precisa existir
 * para uso direto do servico (testes, tarefas internas). O sentinela e {@code "-"}
 * de proposito: nao e um hex de 64 caracteres, entao nunca colide com o escopo de
 * um cliente real.
 */
@Component
public class ClientScope {

    /** Escopo usado quando nao ha cliente autenticado. */
    public static final String ANONYMOUS = "-";

    /** Escopo do cliente da requisicao em curso. */
    public String current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ANONYMOUS;
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? ANONYMOUS : Hashing.sha256Hex(name);
    }
}
