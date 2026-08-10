package com.lucasandrade.bankapi.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash SHA-256 em hexadecimal, usado pela tabela de controle de idempotencia.
 *
 * <p>Existe para que a {@code idempotency_keys} guarde <b>identificadores
 * derivados</b> em vez de copias dos dados originais: a impressao digital da
 * requisicao ({@code request_fingerprint}) e a do cliente dono da chave
 * ({@code client_id}). Nos dois casos a comparacao so precisa responder "e o
 * mesmo?", nunca reconstruir o original — entao uma tabela de controle nao vira
 * copia dos dados da operacao nem do cadastro de usuarios.
 *
 * <p>Bonus pratico: a saida tem <b>largura fixa</b> (64 chars), entao nenhuma das
 * duas colunas depende do tamanho do que entrou.
 */
final class Hashing {

    /** Tamanho da saida em hexadecimal — o mesmo das colunas que a recebem. */
    static final int HEX_LENGTH = 64;

    private Hashing() {
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 e obrigatorio em toda JVM; se faltar, o ambiente esta quebrado.
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }
}
