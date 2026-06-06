package org.bank.moneytransfer.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.bank.moneytransfer.domain.Account;
import org.bank.moneytransfer.domain.AccountStatus;
import org.bank.moneytransfer.dto.AccountResponse;
import org.bank.moneytransfer.dto.CreateAccountRequest;
import org.bank.moneytransfer.dto.LedgerEntryResponse;
import org.bank.moneytransfer.dto.PageResponse;
import org.bank.moneytransfer.exception.ApiException;
import org.bank.moneytransfer.repository.AccountRepository;
import org.bank.moneytransfer.repository.LedgerEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final CurrentUserService currentUser;
    private final IdGenerator ids;
    private final AuditService audit;
    private final OutboxService outbox;
    private final MeterRegistry meterRegistry;

    public AccountService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository,
                          CurrentUserService currentUser, IdGenerator ids, AuditService audit,
                          OutboxService outbox, MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.currentUser = currentUser;
        this.ids = ids;
        this.audit = audit;
        this.outbox = outbox;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request, String correlationId) {
        String ownerId = currentUser.ownerId();
        BigDecimal balance = normalizeInitialBalance(request.initialBalance());
        Account account = accountRepository.save(new Account(ids.accountId(), ownerId, request.currency(), balance));
        audit.record(ownerId, currentUser.actorId(), "account.create", "account", account.getPublicId(), correlationId, "{}");
        meterRegistry.counter("moneytransfer.account.created", "currency", account.getCurrency()).increment();
        return AccountResponse.from(account, correlationId);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(String accountId, String correlationId) {
        return AccountResponse.from(findOwned(accountId), correlationId);
    }

    @Transactional
    public AccountResponse freeze(String accountId, String correlationId) {
        Account account = findOwned(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_CLOSED", "Closed accounts cannot be frozen.");
        }
        account.freeze();
        audit.record(account.getOwnerId(), currentUser.actorId(), "account.freeze", "account", account.getPublicId(), correlationId, "{}");
        outbox.emit("account.frozen", "account", account.getPublicId(), correlationId, Map.of("accountId", account.getPublicId()));
        return AccountResponse.from(account, correlationId);
    }

    @Transactional
    public AccountResponse unfreeze(String accountId, String correlationId) {
        Account account = findOwned(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_CLOSED", "Closed accounts cannot be unfrozen.");
        }
        account.unfreeze();
        audit.record(account.getOwnerId(), currentUser.actorId(), "account.unfreeze", "account", account.getPublicId(), correlationId, "{}");
        return AccountResponse.from(account, correlationId);
    }

    @Transactional
    public AccountResponse close(String accountId, String correlationId) {
        Account account = findOwned(accountId);
        account.close();
        audit.record(account.getOwnerId(), currentUser.actorId(), "account.close", "account", account.getPublicId(), correlationId, "{}");
        return AccountResponse.from(account, correlationId);
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> transactions(String accountId, int page, int size, String correlationId) {
        Account account = findOwned(accountId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(ledgerEntryRepository.findByAccount(account, pageable).map(LedgerEntryResponse::from), correlationId);
    }

    Account findOwned(String accountId) {
        return accountRepository.findByOwnerIdAndPublicId(currentUser.ownerId(), accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account was not found.",
                        Map.of("accountId", accountId)));
    }

    private BigDecimal normalizeInitialBalance(BigDecimal balance) {
        if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INITIAL_BALANCE", "Initial balance must not be negative.");
        }
        if (balance.stripTrailingZeros().scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT_PRECISION", "Initial balance supports at most two decimal places.");
        }
        return balance.setScale(2, RoundingMode.UNNECESSARY);
    }
}
