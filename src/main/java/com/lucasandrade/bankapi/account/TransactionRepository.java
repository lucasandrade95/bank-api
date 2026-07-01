package com.lucasandrade.bankapi.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Extrato da conta, do lancamento mais recente para o mais antigo, paginado
     * e opcionalmente restrito a uma janela de tempo.
     *
     * <p>{@code from} e {@code to} sao opcionais: quando {@code null} aquele lado
     * da janela fica em aberto (extrato completo). O intervalo e semi-aberto
     * ({@code from} inclusivo, {@code to} exclusivo) — o service converte as datas
     * pedidas pelo cliente para esses limites em UTC.
     *
     * <p>A ordenacao fica fixa na propria query (e nao no {@link Pageable}) para
     * o cliente nunca conseguir mudar a ordem do extrato: ele controla apenas
     * pagina, tamanho e o periodo.
     */
    @Query("""
            select t from Transaction t
            where t.accountId = :accountId
              and (:from is null or t.createdAt >= :from)
              and (:to is null or t.createdAt < :to)
            order by t.createdAt desc, t.id desc
            """)
    Page<Transaction> findStatement(@Param("accountId") UUID accountId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    Pageable pageable);
}
