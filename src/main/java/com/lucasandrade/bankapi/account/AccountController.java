package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.account.dto.AccountResponse;
import com.lucasandrade.bankapi.account.dto.CreateAccountRequest;
import com.lucasandrade.bankapi.account.dto.MoneyOperationRequest;
import com.lucasandrade.bankapi.account.dto.TransferRequest;
import com.lucasandrade.bankapi.account.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
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

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.status(HttpStatus.OK).body("UP");
    }
}
