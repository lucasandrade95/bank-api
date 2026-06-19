package com.lucasandrade.bankapi.account.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** Payload de transferencia: conta destino e valor (sempre positivo). */
public record TransferRequest(

        @NotNull(message = "destinationAccountId e obrigatorio")
        UUID destinationAccountId,

        @NotNull(message = "amount e obrigatorio")
        @Positive(message = "amount deve ser positivo")
        @Digits(integer = 17, fraction = 2, message = "amount deve ter no maximo 2 casas decimais")
        BigDecimal amount
) {
}
