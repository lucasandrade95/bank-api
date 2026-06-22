package com.lucasandrade.bankapi.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Extrato da conta, do lancamento mais recente para o mais antigo. */
    List<Transaction> findByAccountIdOrderByCreatedAtDescIdDesc(UUID accountId);
}
