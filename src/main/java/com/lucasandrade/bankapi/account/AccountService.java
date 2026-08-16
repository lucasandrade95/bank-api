package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.account.OperationMetrics.Operation;
import com.lucasandrade.bankapi.account.dto.AccountLimitsResponse;
import com.lucasandrade.bankapi.account.dto.AccountResponse;
import com.lucasandrade.bankapi.account.dto.CreateAccountRequest;
import com.lucasandrade.bankapi.account.dto.MoneyOperationRequest;
import com.lucasandrade.bankapi.account.dto.StatementSummaryResponse;
import com.lucasandrade.bankapi.account.dto.StatementSummaryResponse.TypeBreakdown;
import com.lucasandrade.bankapi.account.dto.TransactionResponse;
import com.lucasandrade.bankapi.account.dto.TransferRequest;
import com.lucasandrade.bankapi.account.dto.TransferResponse;
import com.lucasandrade.bankapi.shared.BusinessException;
import com.lucasandrade.bankapi.shared.DateRange;
import com.lucasandrade.bankapi.shared.IdempotencyService;
import com.lucasandrade.bankapi.shared.Money;
import com.lucasandrade.bankapi.shared.NotFoundException;
import com.lucasandrade.bankapi.shared.PageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final DailyDebitLimit dailyDebitLimit;

    // Metricas de negocio: contam operacoes concluidas (ou seja, committadas),
    // expostas em /actuator/metrics. Ver OperationMetrics.
    private final OperationMetrics metrics;

    public AccountService(AccountRepository repository,
                          TransactionRepository transactionRepository,
                          IdempotencyService idempotency,
                          OperationMetrics metrics,
                          @Value("${bank.limits.daily-debit}") BigDecimal dailyDebitLimit) {
        this.repository = repository;
        this.transactionRepository = transactionRepository;
        this.idempotency = idempotency;
        this.metrics = metrics;
        this.dailyDebitLimit = new DailyDebitLimit(dailyDebitLimit);
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
     *
     * <p>{@code status} e um filtro opcional por situacao da conta (ex.: so as
     * {@code ACTIVE}, para esconder contas bloqueadas ou encerradas). Quando
     * {@code null}, a listagem traz contas de qualquer situacao. A filtragem e
     * feita no banco (parametro anulavel), mesmo padrao do filtro por tipo do extrato.
     */
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> list(int page, int size, AccountStatus status) {
        return PageResponse.from(
                repository.findByStatus(status, PageRequest.of(page, size))
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
        String description = Transaction.normalizeDescription(request.description());
        String requestData = requestData("deposit", id, request.amount(), description);
        return idempotency.execute(idempotencyKey, requestData, AccountResponse.class, () -> {
            Account account = getAccount(id);
            account.deposit(request.amount());
            repository.save(account);
            record(account, TransactionType.DEPOSIT, request.amount(), null, description);
            metrics.countOnCommit(Operation.DEPOSIT);
            return AccountResponse.from(account);
        });
    }

    /**
     * Saca um valor da conta, respeitando o saldo e o limite diario de debito.
     *
     * <p>A checagem do limite fica DEPOIS do debito no dominio de proposito: assim
     * conta bloqueada/encerrada e saldo insuficiente continuam tendo precedencia na
     * mensagem de erro — quem esta com a conta congelada precisa ouvir isso, nao
     * "limite excedido". Nada e persistido quando o limite estoura: a
     * {@code BusinessException} faz rollback da transacao, e o saldo alterado em
     * memoria morre junto.
     */
    @Transactional
    public AccountResponse withdraw(UUID id, String idempotencyKey, MoneyOperationRequest request) {
        String description = Transaction.normalizeDescription(request.description());
        String requestData = requestData("withdraw", id, request.amount(), description);
        return idempotency.execute(idempotencyKey, requestData, AccountResponse.class, () -> {
            Account account = getAccount(id);
            account.withdraw(request.amount());
            ensureWithinDailyDebitLimit(id, request.amount());
            repository.save(account);
            record(account, TransactionType.WITHDRAWAL, request.amount(), null, description);
            metrics.countOnCommit(Operation.WITHDRAWAL);
            return AccountResponse.from(account);
        });
    }

    /**
     * Transfere um valor da conta origem para a conta destino de forma atomica:
     * debito e credito acontecem na mesma transacao, entao qualquer falha
     * (ex.: saldo insuficiente) faz rollback total e nenhum saldo e alterado.
     *
     * <p>A transferencia consome o limite diario de debito da conta ORIGEM (e so
     * dela): quem envia esta tirando dinheiro da propria conta, quem recebe nao.
     * Fosse o limite so do saque, esvaziar uma conta comprometida seria uma
     * transferencia — o limite existe para cobrir toda saida de dinheiro.
     *
     * <p>As duas contas sao carregadas em ORDEM CANONICA (pelo UUID), nao na ordem
     * origem-depois-destino — ver {@link #loadPair}.
     */
    @Transactional
    public TransferResponse transfer(UUID sourceId, String idempotencyKey, TransferRequest request) {
        String description = Transaction.normalizeDescription(request.description());
        String requestData = requestData(
                "transfer", sourceId, request.amount(), request.destinationAccountId(), description);
        return idempotency.execute(idempotencyKey, requestData, TransferResponse.class, () -> {
            if (sourceId.equals(request.destinationAccountId())) {
                throw new BusinessException("Conta origem e destino devem ser diferentes");
            }
            AccountPair pair = loadPair(sourceId, request.destinationAccountId());
            Account source = pair.source();
            Account destination = pair.destination();

            source.withdraw(request.amount());
            ensureWithinDailyDebitLimit(sourceId, request.amount());
            destination.deposit(request.amount());

            repository.save(source);
            repository.save(destination);
            record(source, TransactionType.TRANSFER_OUT, request.amount(), destination.getId(), description);
            record(destination, TransactionType.TRANSFER_IN, request.amount(), source.getId(), description);
            metrics.countOnCommit(Operation.TRANSFER);
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
     * exemplo, "o extrato de janeiro". A validacao da ordem e a conversao para o
     * intervalo semi-aberto em UTC ficam no {@link DateRange}.
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
        DateRange range = DateRange.of(from, to);
        return PageResponse.from(
                transactionRepository
                        .findStatement(id, range.fromInstant(), range.toInstant(), type,
                                PageRequest.of(page, size))
                        .map(TransactionResponse::from));
    }

    /**
     * Resumo do extrato: totais consolidados do periodo (entradas, saidas, saldo
     * liquido e detalhe por tipo), em vez da lista de lancamentos.
     *
     * <p>{@code from}/{@code to} sao as mesmas datas opcionais e inclusivas do
     * extrato, com a mesma validacao e conversao encapsuladas no {@link DateRange}
     * (intervalo semi-aberto em UTC; periodo invertido volta 400). A soma e feita
     * no banco (uma agregacao {@code group by}), entao o resumo continua barato
     * mesmo numa conta com milhares de lancamentos — nada e carregado em memoria
     * para somar.
     */
    @Transactional(readOnly = true)
    public StatementSummaryResponse statementSummary(UUID id, LocalDate from, LocalDate to) {
        getAccount(id); // garante 404 para conta inexistente
        DateRange range = DateRange.of(from, to);

        List<TransactionRepository.TypeTotal> totals =
                transactionRepository.summarizeByType(id, range.fromInstant(), range.toInstant());

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
     * Situacao dos limites da conta: o teto diario de debito, o quanto ja saiu
     * hoje e o quanto ainda ha disponivel. Existe para o cliente saber quanto
     * pode movimentar ANTES de tentar, em vez de descobrir estourando (422).
     *
     * <p>Usa a MESMA soma no banco ({@link #usedToday}) e a MESMA aritmetica
     * ({@link DailyDebitLimit#remaining}) da checagem que barra as operacoes —
     * consulta e enforcement nao tem como divergir. E uma foto do instante, nao
     * uma reserva: entre consultar e operar, outra operacao pode consumir o
     * limite — a operacao seguinte refaz a checagem de verdade.
     */
    @Transactional(readOnly = true)
    public AccountLimitsResponse limits(UUID id) {
        getAccount(id); // garante 404 para conta inexistente
        BigDecimal usedToday = usedToday(id);
        return new AccountLimitsResponse(new AccountLimitsResponse.DailyDebit(
                dailyDebitLimit.limit(),
                Money.normalize(usedToday),
                dailyDebitLimit.remaining(usedToday)));
    }

    /**
     * Descricao canonica de uma operacao com dinheiro, usada pela idempotencia para
     * saber se uma repeticao da mesma {@code Idempotency-Key} e o MESMO pedido.
     *
     * <p>O valor passa por {@link Money#normalize} para que a comparacao seja de
     * dinheiro, e nao de texto: um retry que manda {@code 10.5} onde antes mandou
     * {@code 10.50} e a mesma operacao e deve receber a resposta guardada, nao um 409.
     * Pelo mesmo motivo a descricao entra ja normalizada
     * ({@link Transaction#normalizeDescription}). Ela FAZ parte da identidade do
     * pedido: reusar a chave com outra descricao e outro pedido (409), porque a
     * resposta guardada nao corresponderia ao lancamento que o cliente pediu.
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

    /**
     * Recusa a operacao se ela estourar o limite diario de debito da conta.
     *
     * <p>O quanto ja saiu hoje vem de uma agregacao no banco (saques e
     * transferencias enviadas desde a meia-noite UTC); a decisao em si mora no
     * {@link DailyDebitLimit}. A soma NAO inclui a operacao em curso: o lancamento
     * dela so e gravado depois desta checagem.
     *
     * <p>Isto e um check-then-act — duas operacoes concorrentes na mesma conta
     * poderiam ler o mesmo "ja usado hoje" e ambas passar. Quem fecha essa janela e
     * o travamento otimista que ja existe: as duas alteram o saldo da MESMA conta,
     * entao a segunda gravacao esbarra na {@code @Version}, recebe 409 e, ao ser
     * repetida, refaz esta checagem com o total ja atualizado.
     */
    private void ensureWithinDailyDebitLimit(UUID accountId, BigDecimal amount) {
        dailyDebitLimit.ensureAllows(usedToday(accountId), amount);
    }

    /** Total ja debitado da conta na janela de hoje (saques e transferencias enviadas). */
    private BigDecimal usedToday(UUID accountId) {
        return transactionRepository.sumAmountByTypeSince(
                accountId, TransactionType.debitTypes(), DailyDebitLimit.windowStart());
    }

    /** As duas contas de uma transferencia, ja carregadas. */
    private record AccountPair(Account source, Account destination) {
    }

    /**
     * Carrega as duas contas de uma transferencia em <b>ordem canonica</b>: sempre a
     * do menor UUID primeiro, independentemente de qual delas e a origem.
     *
     * <p>Isto existe para evitar <b>deadlock</b> no banco. Uma transferencia atualiza
     * duas linhas de {@code accounts} na mesma transacao, e cada UPDATE toma um lock
     * na linha ate o commit. Carregando origem-e-depois-destino, duas transferencias
     * simultaneas em sentidos opostos entre as MESMAS contas travam uma na outra:
     * A→B pega a linha de A e pede a de B enquanto B→A pega a de B e pede a de A —
     * nenhuma solta o que ja tem, e o banco mata uma das duas. Com a ordem canonica
     * as duas pedem os mesmos locks na mesma sequencia, entao a segunda apenas
     * espera a primeira terminar: o ciclo nao chega a se formar.
     *
     * <p>O travamento otimista ({@code @Version}) nao cobre este caso — ele evita
     * <i>lost update</i> (uma gravacao sobrescrever a outra), nao a espera circular
     * por locks de linha, que acontece antes, dentro do banco.
     *
     * <p>Quem define a ordem dos UPDATEs e a ordem de <b>carga</b>, nao a das chamadas
     * a {@code save}: as contas ja estao gerenciadas pela transacao, entao o
     * {@code save} nao emite SQL na hora — o Hibernate emite os UPDATEs no flush,
     * seguindo a ordem em que as entidades entraram no contexto de persistencia.
     * Por isso a ordem e imposta aqui, ao carregar.
     *
     * <p>Efeito colateral aceito: quando NENHUMA das duas contas existe, o 404 cita a
     * conta de menor UUID em vez de sempre a origem. Se so uma nao existe, e ela que e
     * citada, como antes.
     */
    private AccountPair loadPair(UUID sourceId, UUID destinationId) {
        if (sourceId.compareTo(destinationId) < 0) {
            return new AccountPair(getAccount(sourceId), getAccount(destinationId));
        }
        Account destination = getAccount(destinationId);
        return new AccountPair(getAccount(sourceId), destination);
    }

    /** Registra um lancamento no extrato, guardando o saldo resultante da conta. */
    private void record(Account account, TransactionType type, BigDecimal amount,
                        UUID counterpartyId, String description) {
        transactionRepository.save(new Transaction(
                account.getId(), type, amount, account.getBalance(), counterpartyId, description));
    }

    private Account getAccount(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Conta nao encontrada: " + id));
    }
}
