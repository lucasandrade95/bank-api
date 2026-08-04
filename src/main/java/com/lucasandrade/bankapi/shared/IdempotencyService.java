package com.lucasandrade.bankapi.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Garante idempotencia de operacoes com efeito colateral (deposito, saque,
 * transferencia) atraves da {@code Idempotency-Key} enviada pelo cliente.
 *
 * <p>Sem chave, a operacao roda normalmente (comportamento opcional, retro-compativel).
 * Com chave: na primeira vez executa e guarda a resposta; numa repeticao com a
 * mesma chave devolve a resposta original, sem executar o efeito colateral de novo.
 *
 * <p>A chave e <b>vinculada a requisicao que a gerou</b> por uma impressao digital
 * (hash do conjunto operacao + conta + valor). Uma chave so devolve a resposta
 * guardada para uma repeticao <i>daquela mesma</i> requisicao; reenviada com uma
 * requisicao diferente, ela nao e um retry e sim reuso indevido — a API recusa com
 * 409 ({@link IdempotencyConflictException}). Sem esse vinculo, um cliente que
 * reaproveitasse a chave por engano (ex.: uma chave por sessao em vez de uma por
 * operacao) receberia a resposta da operacao ANTERIOR e a nova nunca aconteceria:
 * um saque "confirmado" com o corpo de um deposito, dinheiro que some sem erro.
 *
 * <p>Deve ser chamado <b>de dentro</b> do metodo transacional da operacao, para que
 * a gravacao da chave e o efeito colateral commitem (ou facam rollback) juntos.
 *
 * <p>As chaves tem <b>prazo de validade</b>: uma chave so precisa sobreviver
 * enquanto o cliente ainda pode tentar de novo — ver {@link #purgeExpired()}.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final Duration retention;

    public IdempotencyService(IdempotencyRepository repository,
                              ObjectMapper objectMapper,
                              @Value("${bank.idempotency.retention}") Duration retention) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.retention = retention;
    }

    /**
     * Executa {@code operation} com garantia de idempotencia sob {@code key}.
     *
     * @param key         a Idempotency-Key do cliente; {@code null}/em branco desliga a idempotencia
     * @param requestData descricao canonica da requisicao (operacao + conta + valor); e o que
     *                    diferencia um retry legitimo de um reuso da chave em outra requisicao
     * @param type        o tipo da resposta, usado para desserializar uma resposta guardada
     * @param operation   a operacao a executar na primeira vez que a chave e vista
     * @throws IdempotencyConflictException se a chave ja atendeu uma requisicao diferente
     */
    public <T> T execute(String key, String requestData, Class<T> type, Supplier<T> operation) {
        if (key == null || key.isBlank()) {
            return operation.get();
        }
        String fingerprint = fingerprint(requestData);
        Optional<IdempotencyRecord> existing = repository.findById(key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!fingerprint.equals(record.getRequestFingerprint())) {
                throw new IdempotencyConflictException(
                        "Idempotency-Key ja utilizada para uma requisicao diferente");
            }
            return deserialize(record.getResponseBody(), type);
        }
        T result = operation.get();
        repository.save(new IdempotencyRecord(key, fingerprint, serialize(result)));
        return result;
    }

    /**
     * Apaga as chaves que ja passaram da janela de retencao
     * ({@code bank.idempotency.retention}).
     *
     * <p>A tabela de chaves e um <b>cache de respostas, nao um livro-razao</b>: quem
     * guarda o que aconteceu com o dinheiro e o extrato ({@code transactions}), que
     * e permanente. Sem expurgo, porem, ela cresce para sempre — uma linha por
     * operacao com dinheiro, com o corpo da resposta junto — e essa e uma tabela
     * lida e escrita em <i>toda</i> operacao com {@code Idempotency-Key}: quanto
     * maior o indice, mais caro fica o caminho quente, e o backup carrega para
     * sempre dados que ninguem mais vai consultar.
     *
     * <p>Ninguem mais vai consultar porque uma chave so serve enquanto o cliente
     * ainda pode repetir a requisicao. Retry acontece em segundos ou minutos depois
     * do timeout, nao dias; o padrao do mercado (Stripe, por exemplo) e reter por
     * <b>24 horas</b>, e e o default aqui. A janela e generosa de proposito: apagar
     * cedo demais e o unico erro que importa, porque um retry que chega depois do
     * expurgo <b>reexecuta a operacao</b> — a chave que impediria o deposito em
     * duplicidade nao existe mais. Por isso a retencao e configuravel e deve ser
     * sempre maior que a janela de retry do cliente.
     *
     * <p>O expurgo e best-effort: nao ha problema em uma chave vencida sobreviver
     * ate a proxima execucao, nem em varias instancias rodarem o expurgo ao mesmo
     * tempo (o {@code delete} e idempotente e as duas so removem o que ja venceu).
     * Num deploy com muitas instancias, o passo seguinte seria um lock distribuido
     * (ShedLock) para nao repetir o trabalho — nao para evitar corromper nada.
     */
    @Scheduled(fixedDelayString = "${bank.idempotency.purge-interval}",
            initialDelayString = "${bank.idempotency.purge-interval}")
    @Transactional
    public int purgeExpired() {
        int removed = repository.deleteCreatedBefore(Instant.now().minus(retention));
        if (removed > 0) {
            log.info("Expurgo de Idempotency-Keys vencidas: {} removida(s) (retencao {})",
                    removed, retention);
        }
        return removed;
    }

    /**
     * Impressao digital da requisicao: SHA-256 em hex (64 chars, cabe na coluna e
     * nao cresce com o tamanho da entrada). E hash, e nao a descricao crua, para
     * nao guardar dados da operacao numa tabela de controle — a comparacao so
     * precisa responder "e a mesma requisicao?", nunca reconstruir o original.
     */
    private static String fingerprint(String requestData) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(requestData.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 e obrigatorio em toda JVM; se faltar, o ambiente esta quebrado.
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar resposta idempotente", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao ler resposta idempotente guardada", e);
        }
    }
}
