package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.account.dto.AccountResponse;
import com.lucasandrade.bankapi.account.dto.CreateAccountRequest;
import com.lucasandrade.bankapi.shared.BusinessException;
import com.lucasandrade.bankapi.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        if (repository.existsByDocument(request.document())) {
            throw new BusinessException("Ja existe conta para o documento informado");
        }
        Account account = new Account(request.ownerName(), request.document());
        return AccountResponse.from(repository.save(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(UUID id) {
        Account account = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Conta nao encontrada: " + id));
        return AccountResponse.from(account);
    }
}
