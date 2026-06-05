package org.bank.moneytransfer.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.bank.moneytransfer.config.CorrelationIds;
import org.bank.moneytransfer.dto.AccountResponse;
import org.bank.moneytransfer.dto.CreateAccountRequest;
import org.bank.moneytransfer.dto.LedgerEntryResponse;
import org.bank.moneytransfer.dto.PageResponse;
import org.bank.moneytransfer.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@RequestBody @Valid CreateAccountRequest request, HttpServletRequest httpRequest) {
        return accountService.create(request, CorrelationIds.current(httpRequest));
    }

    @GetMapping("/accounts/{accountId}")
    public AccountResponse get(@PathVariable String accountId, HttpServletRequest httpRequest) {
        return accountService.get(accountId, CorrelationIds.current(httpRequest));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public PageResponse<LedgerEntryResponse> transactions(@PathVariable String accountId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size,
                                                          HttpServletRequest httpRequest) {
        return accountService.transactions(accountId, page, size, CorrelationIds.current(httpRequest));
    }

    @PatchMapping("/accounts/{accountId}/freeze")
    public AccountResponse freeze(@PathVariable String accountId, HttpServletRequest httpRequest) {
        return accountService.freeze(accountId, CorrelationIds.current(httpRequest));
    }

    @PatchMapping("/accounts/{accountId}/unfreeze")
    public AccountResponse unfreeze(@PathVariable String accountId, HttpServletRequest httpRequest) {
        return accountService.unfreeze(accountId, CorrelationIds.current(httpRequest));
    }

    @PatchMapping("/accounts/{accountId}/close")
    public AccountResponse close(@PathVariable String accountId, HttpServletRequest httpRequest) {
        return accountService.close(accountId, CorrelationIds.current(httpRequest));
    }
}
