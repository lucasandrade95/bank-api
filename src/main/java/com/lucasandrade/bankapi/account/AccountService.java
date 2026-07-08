package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.account.dto.AccountResponse;
import com.lucasandrade.bankapi.account.dto.CreateAccountRequest;
import com.lucasandrade.bankapi.account.dto.MoneyOperationRequest;
import com.lucasandrade.bankapi.account.dto.TransactionResponse;
import com.lucasandrade.bankapi.account.dto.TransferRequest;
import com.lucasandrade.bankapi.account.dto.TransferResponse;
import com.lucasandrade.bankapi.shared.BusinessException;
import com.lucasandrade.bankapi.shared.IdempotencyService;
import com.lucasandrade.bankapi.shared.NotFoundException;
import com.lucasandrade.bankapi.shared.PageResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository repository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotency;

    // Metricas de negocio: contam operacoes concluidas, expostas em /actuator/metrics.
    private final Counter depositCounter;
    private final Counter withdrawalCounter;
    private final Counter transferCounter;

    public AccountService(AccountRepository repository,
                          TransactionRepository transactionRepository,
                          IdempotencyService idempotency,
                          MeterRegistry meterRegistry) {
        this.repository = repository;
        this.transactionRepository = transactionRepository;
        this.idempotency = idempotency;
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

    /**
     * Lista contas paginadas (da mais recente para a mais antiga). Como o numero
     * de contas cresce sem limite, a listagem nunca e devolvida por inteiro: o
     * cliente pede uma pagina ({@code page}/{@code size}) e recebe os metadados
     * para saber se ha mais — mesmo envelope {@link PageResponse} do extrato.
     */
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> list(int page, int size) {
        return PageResponse.from(
                repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                        .map(AccountResponse::from));
    }

    /** Congela a conta: bloqueia toda movimentacao ate ser reativada. */
    @Transactional
    public AccountResponse block(UUID id) {
        Account account = getAccount(id);
        account.block();
        return AccountResponse.from(repository.save(account));
    }

    /** Reativa uma conta congelada, voltando a permitir movimentacao. */
    @Transactional
    public AccountResponse unblock(UUID id) {
        Account account = getAccount(id);
        account.unblock();
        return AccountResponse.from(repository.save(account));
    }

    @Transactional
    public AccountResponse deposit(UUID id, String idempotencyKey, MoneyOperationRequest request) {
        return idempotency.execute(idempotencyKey, AccountResponse.class, () -> {
            Account account = getAccount(id);
            account.deposit(request.amount());
            repository.save(account);
            record(account, TransactionType.DEPOSIT, request.amount(), null);
            depositCounter.increment();
            return AccountResponse.from(account);
        });
    }

    @Transactional
    public AccountResponse withdraw(UUID id, String idempotencyKey, MoneyOperationRequest request) {
        return idempotency.execute(idempotencyKey, AccountResponse.class, () -> {
            Account account = getAccount(id);
            account.withdraw(request.amount());
            repository.save(account);
            record(account, TransactionType.WITHDRAWAL, request.amount(), null);
            withdrawalCounter.increment();
            return AccountResponse.from(account);
        });
    }

    /**
     * Transfere um valor da conta origem para a conta destino de forma atomica:
     * debito e credito acontecem na mesma transacao, entao qualquer falha
     * (ex.: saldo insuficiente) faz rollback total e nenhum saldo e alterado.
     */
    @Transactional
    public TransferResponse transfer(UUID sourceId, String idempotencyKey, TransferRequest request) {
        return idempotency.execute(idempotencyKey, TransferResponse.class, () -> {
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
        });
    }

    /**
     * Extrato da conta (lancamentos do mais recente para o mais antigo), paginado.
     *
     * <p>Uma conta pode acumular milhares de lancamentos, entao o extrato nunca e
     * devolvido por inteiro: o cliente pede uma pagina ({@code page}/{@code size})
     * e recebe os metadados para saber se ha mais.
     *
     * <p>{@code from} e {@code to} sao datas opcionais (inclusivas nas duas pontas)
     * que restringem o extrato a um periodo — util para o cliente pedir, por
     * exemplo, "o extrato de janeiro". As datas sao interpretadas em UTC e viram
     * um intervalo semi-aberto {@code [from 00:00, (to+1 dia) 00:00)}, para o dia
     * final entrar inteiro.
     *
     * <p>{@code type} e um filtro opcional por tipo de lancamento (ex.: so os
     * depositos, ou so as pernas de saida de transferencia) — combinavel com o
     * periodo. Quando {@code null}, o extrato traz todos os tipos.
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> statement(UUID id, int page, int size,
                                                       LocalDate from, LocalDate to,
                                                       TransactionType type) {
        getAccount(id); // garante 404 para conta inexistente
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from nao pode ser depois de to");
        }
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return PageResponse.from(
                transactionRepository
                        .findStatement(id, fromInstant, toInstant, type, PageRequest.of(page, size))
                        .map(TransactionResponse::from));
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
