package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.account.dto.AccountResponse;
import com.lucasandrade.bankapi.account.dto.CreateAccountRequest;
import com.lucasandrade.bankapi.account.dto.MoneyOperationRequest;
import com.lucasandrade.bankapi.account.dto.StatementSummaryResponse;
import com.lucasandrade.bankapi.account.dto.StatementSummaryResponse.TypeBreakdown;
import com.lucasandrade.bankapi.account.dto.TransactionResponse;
import com.lucasandrade.bankapi.account.dto.TransferRequest;
import com.lucasandrade.bankapi.account.dto.TransferResponse;
import com.lucasandrade.bankapi.shared.BusinessException;
import com.lucasandrade.bankapi.shared.IdempotencyService;
import com.lucasandrade.bankapi.shared.Money;
import com.lucasandrade.bankapi.shared.NotFoundException;
import com.lucasandrade.bankapi.shared.PageResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {

    /** Mesma mensagem para o duplicado detectado na checagem e para o pego pelo banco. */
    private static final String DUPLICATE_DOCUMENT_MESSAGE = "Ja existe conta para o documento informado";

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

    /**
     * Cria uma conta com saldo zero. Um documento (CPF) so pode ter uma conta.
     *
     * <p>A checagem previa ({@code existsByDocument}) e um <b>check-then-act</b>: entre
     * o "ja existe?" e o INSERT ha uma janela em que outra requisicao com o mesmo
     * documento pode inserir primeiro. Quem perde a corrida esbarra na restricao
     * UNIQUE da coluna — e o banco, nao a checagem, que garante a unicidade de fato.
     * Por isso a violacao e traduzida para a MESMA {@link BusinessException} do
     * caminho feliz: o cliente recebe 422 com a mesma mensagem, tenha ele perdido a
     * corrida ou nao. Sem essa traducao a excecao subia para o handler generico de
     * {@code DataIntegrityViolationException} e virava um 409 falando de
     * "Idempotency-Key duplicada" — resposta enganosa para um CPF repetido.
     *
     * <p>O {@code saveAndFlush} e necessario para o INSERT (e a violacao) acontecerem
     * aqui dentro do {@code try}, e nao la no commit da transacao, fora do alcance
     * deste catch.
     */
    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        if (repository.existsByDocument(request.document())) {
            throw new BusinessException(DUPLICATE_DOCUMENT_MESSAGE);
        }
        Account account = new Account(request.ownerName(), request.document());
        try {
            return AccountResponse.from(repository.saveAndFlush(account));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(DUPLICATE_DOCUMENT_MESSAGE);
        }
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

    /**
     * Encerra a conta a pedido do titular. Estado terminal: so e permitido com
     * saldo zero e, uma vez encerrada, a conta nao movimenta nem muda de status.
     */
    @Transactional
    public AccountResponse close(UUID id) {
        Account account = getAccount(id);
        account.close();
        return AccountResponse.from(repository.save(account));
    }

    @Transactional
    public AccountResponse deposit(UUID id, String idempotencyKey, MoneyOperationRequest request) {
        String requestData = requestData("deposit", id, request.amount());
        return idempotency.execute(idempotencyKey, requestData, AccountResponse.class, () -> {
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
        String requestData = requestData("withdraw", id, request.amount());
        return idempotency.execute(idempotencyKey, requestData, AccountResponse.class, () -> {
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
        String requestData = requestData(
                "transfer", sourceId, request.amount(), request.destinationAccountId());
        return idempotency.execute(idempotencyKey, requestData, TransferResponse.class, () -> {
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

    /**
     * Resumo do extrato: totais consolidados do periodo (entradas, saidas, saldo
     * liquido e detalhe por tipo), em vez da lista de lancamentos.
     *
     * <p>{@code from}/{@code to} sao as mesmas datas opcionais e inclusivas do
     * extrato, interpretadas em UTC no mesmo intervalo semi-aberto. A soma e feita
     * no banco (uma agregacao {@code group by}), entao o resumo continua barato
     * mesmo numa conta com milhares de lancamentos — nada e carregado em memoria
     * para somar. Um periodo invertido ({@code from} depois de {@code to}) volta
     * 400, igual ao extrato.
     */
    @Transactional(readOnly = true)
    public StatementSummaryResponse statementSummary(UUID id, LocalDate from, LocalDate to) {
        getAccount(id); // garante 404 para conta inexistente
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from nao pode ser depois de to");
        }
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<TransactionRepository.TypeTotal> totals =
                transactionRepository.summarizeByType(id, fromInstant, toInstant);

        Map<TransactionType, TypeBreakdown> byType = new EnumMap<>(TransactionType.class);
        long totalCount = 0;
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        for (TransactionRepository.TypeTotal total : totals) {
            byType.put(total.getType(), new TypeBreakdown(total.getCount(), Money.normalize(total.getTotal())));
            totalCount += total.getCount();
            if (total.getType().isCredit()) {
                totalIn = totalIn.add(total.getTotal());
            } else {
                totalOut = totalOut.add(total.getTotal());
            }
        }
        // Normaliza os totais para a escala monetaria canonica (2 casas), como todo
        // valor que a API devolve: sem isto, um periodo vazio voltaria "0" em vez de
        // "0.00" (BigDecimal.ZERO tem escala 0). Ver Money.normalize / decisao de design.
        return new StatementSummaryResponse(
                totalCount,
                Money.normalize(totalIn),
                Money.normalize(totalOut),
                Money.normalize(totalIn.subtract(totalOut)),
                byType);
    }

    /**
     * Busca um unico lancamento do extrato (o "comprovante" de uma operacao)
     * pelo seu id, dentro de uma conta.
     *
     * <p>A busca e escopada a conta ({@code accountId} do path): um id de
     * lancamento que existe mas pertence a OUTRA conta devolve 404, nunca o
     * comprovante alheio. Conta inexistente tambem devolve 404.
     */
    @Transactional(readOnly = true)
    public TransactionResponse findTransaction(UUID accountId, UUID transactionId) {
        getAccount(accountId); // garante 404 para conta inexistente
        return transactionRepository.findByIdAndAccountId(transactionId, accountId)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new NotFoundException("Lancamento nao encontrado: " + transactionId));
    }

    /**
     * Descricao canonica de uma operacao com dinheiro, usada pela idempotencia para
     * saber se uma repeticao da mesma {@code Idempotency-Key} e o MESMO pedido.
     *
     * <p>O valor passa por {@link Money#normalize} para que a comparacao seja de
     * dinheiro, e nao de texto: um retry que manda {@code 10.5} onde antes mandou
     * {@code 10.50} e a mesma operacao e deve receber a resposta guardada, nao um 409.
     */
    private static String requestData(String operation, UUID accountId, BigDecimal amount,
                                      Object... extras) {
        StringBuilder sb = new StringBuilder()
                .append(operation).append('|')
                .append(accountId).append('|')
                .append(Money.normalize(amount));
        for (Object extra : extras) {
            sb.append('|').append(extra);
        }
        return sb.toString();
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
