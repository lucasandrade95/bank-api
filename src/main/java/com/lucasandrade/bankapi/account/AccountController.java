package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.account.dto.AccountResponse;
import com.lucasandrade.bankapi.account.dto.CreateAccountRequest;
import com.lucasandrade.bankapi.account.dto.MoneyOperationRequest;
import com.lucasandrade.bankapi.account.dto.TransactionResponse;
import com.lucasandrade.bankapi.account.dto.TransferRequest;
import com.lucasandrade.bankapi.account.dto.TransferResponse;
import com.lucasandrade.bankapi.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        AccountResponse created = service.create(request);
        URI location = uriBuilder.path("/api/v1/accounts/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<AccountResponse> deposit(@PathVariable UUID id,
                                                   @Valid @RequestBody MoneyOperationRequest request) {
        return ResponseEntity.ok(service.deposit(id, request));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<AccountResponse> withdraw(@PathVariable UUID id,
                                                    @Valid @RequestBody MoneyOperationRequest request) {
        return ResponseEntity.ok(service.withdraw(id, request));
    }

    @PostMapping("/{id}/transfer")
    public ResponseEntity<TransferResponse> transfer(@PathVariable UUID id,
                                                     @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(service.transfer(id, request));
    }

    @PostMapping("/{id}/block")
    public ResponseEntity<AccountResponse> block(@PathVariable UUID id) {
        return ResponseEntity.ok(service.block(id));
    }

    @PostMapping("/{id}/unblock")
    public ResponseEntity<AccountResponse> unblock(@PathVariable UUID id) {
        return ResponseEntity.ok(service.unblock(id));
    }

    @GetMapping("/{id}/statement")
    public ResponseEntity<PageResponse<TransactionResponse>> statement(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.statement(id, page, size, from, to));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.status(HttpStatus.OK).body("UP");
    }
}
