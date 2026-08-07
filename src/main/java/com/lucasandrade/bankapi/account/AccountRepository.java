package com.lucasandrade.bankapi.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByDocument(String document);

    /**
     * Lista contas paginadas, sempre da mais recente para a mais antiga, com um
     * filtro opcional por situacao.
     *
     * <p>{@code status} e opcional: quando {@code null} o filtro fica em aberto e a
     * listagem traz contas de qualquer situacao; quando informado, restringe a um
     * unico {@link AccountStatus} (ex.: so as {@code ACTIVE}) — a filtragem e feita
     * no banco, mesmo padrao (parametro anulavel) do filtro por tipo do extrato.
     *
     * <p>A ordenacao fica fixa na propria query (nao no {@link Pageable}), entao o
     * cliente controla so pagina, tamanho e situacao e nunca muda a ordem do servidor.
     *
     * <p>O {@code a.id desc} nao e enfeite: e o <b>desempate</b> que torna a ordenacao
     * uma ordem TOTAL. {@code createdAt} sozinho nao e unico — duas contas criadas no
     * mesmo instante (concorrencia sob carga, uma carga em lote vinda de um sistema
     * legado) empatam, e para linhas empatadas o banco pode devolver a ordem que
     * quiser, inclusive uma ordem DIFERENTE a cada execucao. Como a paginacao por
     * {@code offset} recorta a lista por posicao, uma ordem que muda entre a pagina 1
     * e a pagina 2 faz uma conta <b>aparecer duas vezes</b> e outra <b>nunca aparecer</b>
     * — sem erro nenhum, so um registro que some da listagem. Com o id no desempate
     * nao existe empate: a ordem e a mesma em toda execucao e o recorte por pagina fica
     * estavel. Mesmo cuidado que a query do extrato ({@code TransactionRepository.findStatement})
     * ja tomava.
     */
    @Query("""
            select a from Account a
            where (:status is null or a.status = :status)
            order by a.createdAt desc, a.id desc
            """)
    Page<Account> findByStatus(@Param("status") AccountStatus status, Pageable pageable);
}
