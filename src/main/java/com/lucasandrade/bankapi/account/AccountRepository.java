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
     */
    @Query("""
            select a from Account a
            where (:status is null or a.status = :status)
            order by a.createdAt desc
            """)
    Page<Account> findByStatus(@Param("status") AccountStatus status, Pageable pageable);
}
