package com.lucasandrade.bankapi.shared;

/** Recurso nao encontrado. Mapeado para HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
