package com.lucasandrade.bankapi.account.dto;

import com.lucasandrade.bankapi.account.Transaction;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload de deposito ou saque. Valor em BigDecimal, sempre positivo; descricao
 * livre opcional, que aparece no extrato.
 */
public record MoneyOperationRequest(

        @NotNull(message = "amount e obrigatorio")
        @Positive(message = "amount deve ser positivo")
        @Digits(integer = 17, fraction = 2, message = "amount deve ter no maximo 2 casas decimais")
        BigDecimal amount,

        @Size(max = Transaction.DESCRIPTION_MAX_LENGTH,
                message = "description deve ter no maximo " + Transaction.DESCRIPTION_MAX_LENGTH + " caracteres")
        String description
) {
}
