package com.lucasandrade.bankapi.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 */
@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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
