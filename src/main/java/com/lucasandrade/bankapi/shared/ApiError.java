package com.lucasandrade.bankapi.shared;

import java.time.Instant;
import java.util.List;

/** Corpo padrao de erro retornado pela API. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        List<String> messages
) {
    public static ApiError of(int status, String error, List<String> messages) {
        return new ApiError(Instant.now(), status, error, messages);
    }
}
