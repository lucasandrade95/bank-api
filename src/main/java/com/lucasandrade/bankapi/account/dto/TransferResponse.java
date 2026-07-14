package com.lucasandrade.bankapi.account.dto;

import com.lucasandrade.bankapi.account.Account;
import com.lucasandrade.bankapi.shared.Money;

import java.math.BigDecimal;

/** Resultado de uma transferencia: estado final das duas contas e valor movido. */
public record TransferResponse(
        AccountResponse source,
        AccountResponse destination,
        BigDecimal amount
) {
    public static TransferResponse of(Account source, Account destination, BigDecimal amount) {
        return new TransferResponse(
                AccountResponse.from(source),
                AccountResponse.from(destination),
                Money.normalize(amount)
        );
    }
}
