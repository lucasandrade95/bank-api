package com.lucasandrade.bankapi.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Apaga as chaves gravadas antes de {@code cutoff} (as que ja sairam da janela
     * de retencao) e devolve quantas foram removidas.
     *
     * <p>E um {@code delete} em massa, feito no banco numa unica instrucao — e nao
     * o {@code deleteByCreatedAtBefore} derivado, que o Spring Data implementa
     * carregando cada linha como entidade para depois remove-la uma a uma. Num
     * expurgo que pode encontrar milhares de chaves de um dia inteiro de operacoes,
     * a diferenca e carregar tudo em memoria ou nao carregar nada.
     */
    @Modifying
    @Query("delete from IdempotencyRecord r where r.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
