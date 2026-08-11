package com.lucasandrade.bankapi.account;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Metrica de negocio {@code bank.account.operations}: quantas operacoes bancarias
 * foram <b>concluidas</b>, separadas pela tag {@code type}.
 *
 * <p>A palavra que carrega o peso e "concluidas". Uma operacao com dinheiro so
 * aconteceu de verdade quando a transacao <b>commita</b>: ate la o saldo alterado
 * vive apenas no contexto de persistencia, e o passo mais provavel de falhar e
 * justamente o ultimo. O {@code save} de uma entidade ja gerenciada nao emite SQL
 * na hora — o Hibernate so manda os UPDATEs no <i>flush</i>, e e ali que a
 * checagem do travamento otimista ({@code WHERE version = ?}) roda. Ou seja: uma
 * operacao pode passar por todas as regras do dominio, chegar ao fim do metodo do
 * service e ainda assim <b>nao acontecer</b>, virando 409 no cliente.
 *
 * <p>Contar dentro da transacao, como era feito antes, conta essas tentativas
 * junto com os sucessos, e erra exatamente onde mais dói: a contagem infla sob
 * <b>contencao</b> — quando duas operacoes disputam a mesma conta e uma e
 * rejeitada —, que e quando alguem esta olhando o painel para entender o que esta
 * acontecendo. Sao ao menos tres caminhos que abortam depois da contagem: a falha
 * otimista no flush (409), a colisao da mesma {@code Idempotency-Key} enviada em
 * paralelo (409, a chave e gravada logo apos a operacao) e qualquer falha de
 * infraestrutura no commit. O sintoma e silencioso: nenhum erro, so um total de
 * depositos que nunca fecha com o extrato — e o extrato, gravado na mesma
 * transacao, e quem esta certo.
 *
 * <p>Por isso a contagem e adiada para <b>depois do commit</b>, via
 * {@link TransactionSynchronization}: se a transacao faz rollback, o
 * {@code afterCommit} simplesmente nunca roda e nada e contado. O <i>hook</i> roda
 * de forma sincrona, na mesma thread, antes de o metodo transacional devolver o
 * controle — entao a metrica ja esta atualizada quando a resposta HTTP e escrita.
 *
 * <p>O erro fica assimetrico de proposito, e do lado seguro: uma operacao
 * committada e sempre contada, e uma abortada nunca e. Nao ha o inverso (contar a
 * mais) porque nada depois do commit pode desfazer a operacao.
 *
 * <p>Sem transacao ativa a contagem e imediata: nao existe commit para esperar, e
 * um contador que se recusasse a contar seria pior que um contador aproximado.
 * Na pratica isso nao acontece pela API — toda operacao com dinheiro e
 * {@code @Transactional} —, mas o servico continua chamavel direto (testes,
 * tarefas internas).
 */
@Component
public class OperationMetrics {

    /** Operacao de negocio contada, um valor por tag {@code type}. */
    public enum Operation {

        DEPOSIT("deposit"),
        WITHDRAWAL("withdrawal"),
        TRANSFER("transfer");

        private final String tag;

        Operation(String tag) {
            this.tag = tag;
        }

        /** Valor da tag {@code type} na metrica — parte do contrato com o painel. */
        public String tag() {
            return tag;
        }
    }

    private final Map<Operation, Counter> counters;

    /**
     * Registra os contadores no start da aplicacao, e nao na primeira operacao:
     * uma metrica que so aparece depois do primeiro deposito faz o painel comecar
     * com uma serie ausente em vez de um zero.
     */
    public OperationMetrics(MeterRegistry registry) {
        EnumMap<Operation, Counter> byOperation = new EnumMap<>(Operation.class);
        for (Operation operation : Operation.values()) {
            byOperation.put(operation, Counter.builder("bank.account.operations")
                    .description("Total de operacoes bancarias concluidas")
                    .tag("type", operation.tag())
                    .register(registry));
        }
        this.counters = Collections.unmodifiableMap(byOperation);
    }

    /**
     * Conta a operacao <b>se e quando</b> a transacao em curso commitar. Um
     * rollback (saldo insuficiente, conta bloqueada, conflito de concorrencia no
     * flush) deixa a metrica intocada.
     */
    public void countOnCommit(Operation operation) {
        Counter counter = counters.get(operation);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            counter.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                counter.increment();
            }
        });
    }
}
