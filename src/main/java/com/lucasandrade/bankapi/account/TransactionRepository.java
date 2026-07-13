package com.lucasandrade.bankapi.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Busca um unico lancamento pelo id, restrito a uma conta. O escopo por
     * {@code accountId} e proposital: um id de lancamento valido mas de OUTRA
     * conta devolve vazio (404), em vez de expor o extrato alheio.
     */
    Optional<Transaction> findByIdAndAccountId(UUID id, UUID accountId);

    /**
     * Extrato da conta, do lancamento mais recente para o mais antigo, paginado
     * e opcionalmente restrito a uma janela de tempo.
     *
     * <p>{@code from}, {@code to} e {@code type} sao opcionais: quando {@code null}
     * aquele filtro fica em aberto (extrato completo). O intervalo de tempo e
     * semi-aberto ({@code from} inclusivo, {@code to} exclusivo) — o service
     * converte as datas pedidas pelo cliente para esses limites em UTC — e
     * {@code type} restringe a um unico tipo de lancamento (ex.: so os depositos).
     *
     * <p>A ordenacao fica fixa na propria query (e nao no {@link Pageable}) para
     * o cliente nunca conseguir mudar a ordem do extrato: ele controla apenas
     * pagina, tamanho, periodo e tipo.
     */
    @Query("""
            select t from Transaction t
            where t.accountId = :accountId
              and (:from is null or t.createdAt >= :from)
              and (:to is null or t.createdAt < :to)
              and (:type is null or t.type = :type)
            order by t.createdAt desc, t.id desc
            """)
    Page<Transaction> findStatement(@Param("accountId") UUID accountId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    @Param("type") TransactionType type,
                                    Pageable pageable);

    /**
     * Totaliza os lancamentos da conta por tipo, opcionalmente restrito a uma
     * janela de tempo (mesmo intervalo semi-aberto do extrato). A agregacao
     * (contagem e soma dos valores) e feita no banco com um {@code group by} —
     * o resumo do extrato nunca carrega os lancamentos um a um para somar em
     * memoria, entao continua barato numa conta com milhares de movimentacoes.
     */
    @Query("""
            select t.type as type, count(t) as count, coalesce(sum(t.amount), 0) as total
            from Transaction t
            where t.accountId = :accountId
              and (:from is null or t.createdAt >= :from)
              and (:to is null or t.createdAt < :to)
            group by t.type
            """)
    List<TypeTotal> summarizeByType(@Param("accountId") UUID accountId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    /** Projecao do total agregado de um tipo de lancamento no periodo. */
    interface TypeTotal {
        TransactionType getType();

        long getCount();

        BigDecimal getTotal();
    }
}
