package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.account.dto.AccountResponse;
import com.lucasandrade.bankapi.shared.PageResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Estabilidade da paginacao da listagem de contas quando o {@code createdAt} empata.
 *
 * <p>A listagem ordena por {@code createdAt desc}, mas {@code createdAt} nao e unico:
 * contas criadas no mesmo instante (concorrencia sob carga, carga em lote de um
 * sistema legado) empatam. Para linhas empatadas o banco pode devolver a ordem que
 * quiser — e ate uma ordem diferente a cada execucao. Como a paginacao por
 * {@code offset} recorta a lista por posicao, uma ordem instavel faz uma conta
 * aparecer em duas paginas e outra nunca aparecer.
 *
 * <p>Uma reordenacao de empate nao e reproduzivel de forma deterministica num teste
 * (depende do plano de execucao do banco), entao o que estes testes fixam e a
 * <b>invariante que a impede</b>: a ordenacao e uma ordem TOTAL, com o id desempatando
 * quem tem o mesmo {@code createdAt}.
 *
 * <p>As contas do teste sao inseridas com SQL nativo por dois motivos: o
 * {@code createdAt} da entidade e {@code updatable = false} e sempre {@code Instant.now()}
 * (nao da para forcar o empate pela API do dominio), e os ids precisam ser
 * <b>escolhidos</b>, nao gerados — ver {@link #ORDERED_IDS}.
 */
@SpringBootTest
@Transactional
class AccountListingOrderTest {

    /**
     * Ids escolhidos a dedo, em ordem crescente, para o teste poder afirmar qual e a
     * ordem esperada sem depender de como o banco compara UUID.
     *
     * <p>Java compara UUID como dois {@code long} <b>com sinal</b>; bancos costumam
     * compara-los como 16 bytes <b>sem sinal</b> — para um UUID aleatorio as duas
     * ordens divergem. Estes seis diferem apenas no ultimo digito e compartilham todo
     * o resto, entao as duas comparacoes concordam entre si e a ordem esperada e
     * simplesmente a ordem em que estao escritos.
     */
    private static final List<UUID> ORDERED_IDS = List.of(
            UUID.fromString("00000000-0000-4000-8000-0000000000a1"),
            UUID.fromString("00000000-0000-4000-8000-0000000000a2"),
            UUID.fromString("00000000-0000-4000-8000-0000000000a3"),
            UUID.fromString("00000000-0000-4000-8000-0000000000a4"),
            UUID.fromString("00000000-0000-4000-8000-0000000000a5"),
            UUID.fromString("00000000-0000-4000-8000-0000000000a6"));

    /** Ordem esperada na listagem: mais recente primeiro e, no empate, maior id primeiro. */
    private static final List<UUID> EXPECTED_ORDER = ORDERED_IDS.reversed();

    /**
     * Instante compartilhado pelas seis contas (o empate) e bem no futuro, para elas
     * ocuparem o topo da listagem independentemente do que outros testes ja gravaram
     * no H2 compartilhado.
     */
    private static final Instant TIE = Instant.now().plus(365, ChronoUnit.DAYS);

    @Autowired
    private AccountService service;

    @Autowired
    private EntityManager entityManager;

    /**
     * Insere as seis contas empatadas. Roda dentro da transacao do teste, entao tudo
     * e desfeito no fim e nada vaza para o H2 compartilhado pelos demais testes.
     */
    @BeforeEach
    void insertAccountsSharingCreatedAt() {
        for (int i = 0; i < ORDERED_IDS.size(); i++) {
            entityManager.createNativeQuery("""
                            insert into accounts (id, owner_name, document, balance, status, created_at, version)
                            values (?1, ?2, ?3, 0.00, 'ACTIVE', ?4, 0)
                            """)
                    .setParameter(1, ORDERED_IDS.get(i))
                    .setParameter(2, "Titular " + i)
                    .setParameter(3, String.format("9%010d", i))
                    .setParameter(4, Timestamp.from(TIE))
                    .executeUpdate();
        }
    }

    @Test
    void tiedCreatedAt_isBrokenByIdDescending() {
        List<UUID> head = idsOf(service.list(0, ORDERED_IDS.size(), null));

        assertThat(head).containsExactlyElementsOf(EXPECTED_ORDER);
    }

    /**
     * A consequencia pratica do desempate: percorrendo a listagem pagina a pagina,
     * cada conta empatada aparece exatamente uma vez — nenhuma repetida, nenhuma
     * pulada. E isto que o {@code offset} nao consegue garantir sozinho quando a
     * ordem das linhas empatadas pode mudar entre uma pagina e a seguinte.
     */
    @Test
    void pagingOverTiedAccounts_returnsEachAccountExactlyOnce() {
        int pageSize = 2;
        List<UUID> seen = new ArrayList<>();
        for (int page = 0; page < ORDERED_IDS.size() / pageSize; page++) {
            seen.addAll(idsOf(service.list(page, pageSize, null)));
        }

        assertThat(seen).containsExactlyElementsOf(EXPECTED_ORDER);
        assertThat(seen).doesNotHaveDuplicates();
    }

    /** O filtro por situacao usa a mesma query, entao o desempate tambem vale nele. */
    @Test
    void tieBreakerAlsoAppliesWhenFilteringByStatus() {
        List<UUID> head = idsOf(service.list(0, ORDERED_IDS.size(), AccountStatus.ACTIVE));

        assertThat(head).containsExactlyElementsOf(EXPECTED_ORDER);
    }

    private static List<UUID> idsOf(PageResponse<AccountResponse> page) {
        return page.content().stream().map(AccountResponse::id).toList();
    }
}
