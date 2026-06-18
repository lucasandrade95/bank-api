package com.lucasandrade.bankapi.shared;

/** Erro de regra de negocio. Mapeado para HTTP 422. */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
