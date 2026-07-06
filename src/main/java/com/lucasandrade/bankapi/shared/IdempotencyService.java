package com.lucasandrade.bankapi.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

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
     * @param key       a Idempotency-Key do cliente; {@code null}/em branco desliga a idempotencia
     * @param type      o tipo da resposta, usado para desserializar uma resposta guardada
     * @param operation a operacao a executar na primeira vez que a chave e vista
     */
    public <T> T execute(String key, Class<T> type, Supplier<T> operation) {
        if (key == null || key.isBlank()) {
            return operation.get();
        }
        Optional<IdempotencyRecord> existing = repository.findById(key);
        if (existing.isPresent()) {
            return deserialize(existing.get().getResponseBody(), type);
        }
        T result = operation.get();
        repository.save(new IdempotencyRecord(key, serialize(result)));
        return result;
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
