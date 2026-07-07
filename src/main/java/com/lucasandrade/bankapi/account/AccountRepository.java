package com.lucasandrade.bankapi.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByDocument(String document);

    /**
     * Lista contas paginadas, sempre da mais recente para a mais antiga. A ordem
     * fica fixa no nome do metodo (nao no {@code Pageable}), entao o cliente
     * controla so pagina e tamanho e nunca muda a ordenacao do servidor.
     */
    Page<Account> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
