package com.lucasandrade.bankapi.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Extrato da conta, do lancamento mais recente para o mais antigo, paginado.
     *
     * <p>A ordenacao fica fixa no nome do metodo (e nao no {@link Pageable}) para
     * o cliente nunca conseguir mudar a ordem do extrato: ele controla apenas
     * pagina e tamanho.
     */
    Page<Transaction> findByAccountIdOrderByCreatedAtDescIdDesc(UUID accountId, Pageable pageable);
}
