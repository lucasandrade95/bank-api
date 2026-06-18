package com.lucasandrade.bankapi.account.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Payload de deposito ou saque. Valor em BigDecimal, sempre positivo. */
public record MoneyOperationRequest(

        @NotNull(message = "amount e obrigatorio")
        @Positive(message = "amount deve ser positivo")
        @Digits(integer = 17, fraction = 2, message = "amount deve ter no maximo 2 casas decimais")
        BigDecimal amount
) {
}
