package com.lucasandrade.bankapi.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload de criacao de conta. */
public record CreateAccountRequest(

        @NotBlank(message = "ownerName e obrigatorio")
        @Size(max = 120)
        String ownerName,

        @NotBlank(message = "document e obrigatorio")
        @Pattern(regexp = "\\d{11}", message = "document deve ter 11 digitos (CPF)")
        String document
) {
}
