package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.account.dto.AccountResponse;
import com.lucasandrade.bankapi.account.dto.CreateAccountRequest;
import com.lucasandrade.bankapi.account.dto.MoneyOperationRequest;
import com.lucasandrade.bankapi.account.dto.TransferRequest;
import com.lucasandrade.bankapi.account.dto.TransferResponse;
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
        return AccountResponse.from(getAccount(id));
    }

    @Transactional
    public AccountResponse deposit(UUID id, MoneyOperationRequest request) {
        Account account = getAccount(id);
        account.deposit(request.amount());
        return AccountResponse.from(repository.save(account));
    }

    @Transactional
    public AccountResponse withdraw(UUID id, MoneyOperationRequest request) {
        Account account = getAccount(id);
        account.withdraw(request.amount());
        return AccountResponse.from(repository.save(account));
    }

    /**
     * Transfere um valor da conta origem para a conta destino de forma atomica:
     * debito e credito acontecem na mesma transacao, entao qualquer falha
     * (ex.: saldo insuficiente) faz rollback total e nenhum saldo e alterado.
     */
    @Transactional
    public TransferResponse transfer(UUID sourceId, TransferRequest request) {
        if (sourceId.equals(request.destinationAccountId())) {
            throw new BusinessException("Conta origem e destino devem ser diferentes");
        }
        Account source = getAccount(sourceId);
        Account destination = getAccount(request.destinationAccountId());

        source.withdraw(request.amount());
        destination.deposit(request.amount());

        repository.save(source);
        repository.save(destination);
        return TransferResponse.of(source, destination, request.amount());
    }

    private Account getAccount(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Conta nao encontrada: " + id));
    }
}
