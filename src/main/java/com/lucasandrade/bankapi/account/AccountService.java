package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.account.dto.AccountResponse;
import com.lucasandrade.bankapi.account.dto.CreateAccountRequest;
import com.lucasandrade.bankapi.account.dto.MoneyOperationRequest;
import com.lucasandrade.bankapi.account.dto.TransactionResponse;
import com.lucasandrade.bankapi.account.dto.TransferRequest;
import com.lucasandrade.bankapi.account.dto.TransferResponse;
import com.lucasandrade.bankapi.shared.BusinessException;
import com.lucasandrade.bankapi.shared.NotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository repository;
    private final TransactionRepository transactionRepository;

    // Metricas de negocio: contam operacoes concluidas, expostas em /actuator/metrics.
    private final Counter depositCounter;
    private final Counter withdrawalCounter;
    private final Counter transferCounter;

    public AccountService(AccountRepository repository,
                          TransactionRepository transactionRepository,
                          MeterRegistry meterRegistry) {
        this.repository = repository;
        this.transactionRepository = transactionRepository;
        this.depositCounter = operationCounter(meterRegistry, "deposit");
        this.withdrawalCounter = operationCounter(meterRegistry, "withdrawal");
        this.transferCounter = operationCounter(meterRegistry, "transfer");
    }

    /** Counter de operacoes concluidas, diferenciado pela tag {@code type}. */
    private static Counter operationCounter(MeterRegistry registry, String type) {
        return Counter.builder("bank.account.operations")
                .description("Total de operacoes bancarias concluidas")
                .tag("type", type)
                .register(registry);
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
        repository.save(account);
        record(account, TransactionType.DEPOSIT, request.amount(), null);
        depositCounter.increment();
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse withdraw(UUID id, MoneyOperationRequest request) {
        Account account = getAccount(id);
        account.withdraw(request.amount());
        repository.save(account);
        record(account, TransactionType.WITHDRAWAL, request.amount(), null);
        withdrawalCounter.increment();
        return AccountResponse.from(account);
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
        record(source, TransactionType.TRANSFER_OUT, request.amount(), destination.getId());
        record(destination, TransactionType.TRANSFER_IN, request.amount(), source.getId());
        transferCounter.increment();
        return TransferResponse.of(source, destination, request.amount());
    }

    /** Extrato da conta (lancamentos do mais recente para o mais antigo). */
    @Transactional(readOnly = true)
    public List<TransactionResponse> statement(UUID id) {
        getAccount(id); // garante 404 para conta inexistente
        return transactionRepository.findByAccountIdOrderByCreatedAtDescIdDesc(id).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    /** Registra um lancamento no extrato, guardando o saldo resultante da conta. */
    private void record(Account account, TransactionType type, BigDecimal amount, UUID counterpartyId) {
        transactionRepository.save(
                new Transaction(account.getId(), type, amount, account.getBalance(), counterpartyId));
    }

    private Account getAccount(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Conta nao encontrada: " + id));
    }
}
