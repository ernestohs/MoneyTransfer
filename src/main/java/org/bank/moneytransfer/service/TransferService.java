package org.bank.moneytransfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.bank.moneytransfer.domain.Account;
import org.bank.moneytransfer.domain.AccountStatus;
import org.bank.moneytransfer.domain.IdempotencyRecord;
import org.bank.moneytransfer.domain.LedgerDirection;
import org.bank.moneytransfer.domain.LedgerEntry;
import org.bank.moneytransfer.domain.Transfer;
import org.bank.moneytransfer.domain.TransferStatus;
import org.bank.moneytransfer.dto.TransferQuoteResponse;
import org.bank.moneytransfer.dto.TransferRequest;
import org.bank.moneytransfer.dto.TransferResponse;
import org.bank.moneytransfer.exception.ApiException;
import org.bank.moneytransfer.repository.AccountRepository;
import org.bank.moneytransfer.repository.IdempotencyRecordRepository;
import org.bank.moneytransfer.repository.LedgerEntryRepository;
import org.bank.moneytransfer.repository.TransferRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TransferService {
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final CurrentUserService currentUser;
    private final IdGenerator ids;
    private final AmountRules amountRules;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final OutboxService outbox;
    private final MeterRegistry meterRegistry;

    public TransferService(AccountRepository accountRepository, TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository, IdempotencyRecordRepository idempotencyRepository,
                           CurrentUserService currentUser, IdGenerator ids, AmountRules amountRules,
                           ObjectMapper objectMapper, AuditService audit, OutboxService outbox, MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.currentUser = currentUser;
        this.ids = ids;
        this.amountRules = amountRules;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.outbox = outbox;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public TransferQuoteResponse quote(TransferRequest request, String correlationId) {
        BigDecimal amount = amountRules.normalize(request.amount());
        validatePublicAccountIds(request);
        return new TransferQuoteResponse(amount, request.currency(), BigDecimal.ZERO.setScale(2), amount, correlationId);
    }

    @Transactional
    public ResponseEntity<String> create(TransferRequest request, String idempotencyKey, String correlationId) {
        String ownerId = currentUser.ownerId();
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required.");
        }
        String requestHash = requestHash(request);
        return idempotent(ownerId, idempotencyKey, requestHash, () -> {
            long start = System.nanoTime();
            TransferResponse response = executeTransfer(request, idempotencyKey, correlationId, null);
            recordLatency(start, "completed");
            return response;
        });
    }

    @Transactional(readOnly = true)
    public TransferResponse get(String transferId, String correlationId) {
        return TransferResponse.from(findOwnedTransfer(transferId), correlationId);
    }

    @Transactional
    public TransferResponse cancel(String transferId, String correlationId) {
        Transfer transfer = findOwnedTransfer(transferId);
        if (transfer.getStatus() == TransferStatus.COMPLETED || transfer.getStatus() == TransferStatus.REVERSED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRANSFER_NOT_CANCELLABLE", "Only pending or processing transfers can be cancelled.");
        }
        transfer.cancel();
        audit.record(transfer.getOwnerId(), currentUser.actorId(), "transfer.cancel", "transfer", transfer.getPublicId(), correlationId, "{}");
        outbox.emit("transfer.cancelled", "transfer", transfer.getPublicId(), correlationId, Map.of("transferId", transfer.getPublicId()));
        meterRegistry.counter("moneytransfer.transfer.outcome", "status", "cancelled").increment();
        return TransferResponse.from(transfer, correlationId);
    }

    @Transactional
    public TransferResponse reverse(String transferId, String correlationId) {
        Transfer original = findOwnedTransfer(transferId);
        if (original.getStatus() != TransferStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRANSFER_NOT_REVERSIBLE", "Only completed transfers can be reversed.");
        }
        TransferRequest reversalRequest = new TransferRequest(
                original.getDestinationAccount().getPublicId(),
                original.getSourceAccount().getPublicId(),
                original.getAmount(),
                original.getCurrency(),
                "Reversal of " + original.getPublicId()
        );
        TransferResponse reversal = executeTransfer(reversalRequest, null, correlationId, original);
        original.reverse();
        audit.record(original.getOwnerId(), currentUser.actorId(), "transfer.reverse", "transfer", original.getPublicId(), correlationId,
                "{\"reversalTransferId\":\"" + reversal.transferId() + "\"}");
        outbox.emit("transfer.reversed", "transfer", original.getPublicId(), correlationId,
                Map.of("transferId", original.getPublicId(), "reversalTransferId", reversal.transferId()));
        meterRegistry.counter("moneytransfer.transfer.outcome", "status", "reversed").increment();
        return TransferResponse.from(original, correlationId);
    }

    private TransferResponse executeTransfer(TransferRequest request, String idempotencyKey, String correlationId, Transfer reversalOf) {
        String ownerId = currentUser.ownerId();
        BigDecimal amount = amountRules.normalize(request.amount());
        validatePublicAccountIds(request);

        Account source = accountRepository.findByOwnerIdAndPublicId(ownerId, request.sourceAccountId())
                .orElseThrow(() -> notFound("sourceAccountId", request.sourceAccountId()));
        Account destination = accountRepository.findByOwnerIdAndPublicId(ownerId, request.destinationAccountId())
                .orElseThrow(() -> notFound("destinationAccountId", request.destinationAccountId()));

        List<Account> locked = accountRepository.lockAllByIdsOrdered(List.of(source.getId(), destination.getId()));
        List<Account> ordered = locked.stream().sorted(Comparator.comparing(Account::getId)).toList();
        source = ordered.stream().filter(a -> a.getPublicId().equals(request.sourceAccountId())).findFirst().orElseThrow();
        destination = ordered.stream().filter(a -> a.getPublicId().equals(request.destinationAccountId())).findFirst().orElseThrow();

        validateAccounts(source, destination, request.currency(), amount);
        Transfer transfer = transferRepository.save(new Transfer(ids.transferId(), ownerId, source, destination, amount,
                request.currency(), idempotencyKey, request.description()));
        if (reversalOf != null) {
            transfer.markReversalOf(reversalOf);
        }
        outbox.emit("transfer.created", "transfer", transfer.getPublicId(), correlationId, Map.of("transferId", transfer.getPublicId()));
        audit.record(ownerId, currentUser.actorId(), "transfer.create", "transfer", transfer.getPublicId(), correlationId, "{}");

        source.debit(amount);
        destination.credit(amount);
        ledgerEntryRepository.save(new LedgerEntry(ids.ledgerId(), transfer, source, LedgerDirection.DEBIT,
                amount, request.currency(), source.getAvailableBalance(), request.description()));
        ledgerEntryRepository.save(new LedgerEntry(ids.ledgerId(), transfer, destination, LedgerDirection.CREDIT,
                amount, request.currency(), destination.getAvailableBalance(), request.description()));
        transfer.complete();
        audit.record(ownerId, currentUser.actorId(), "transfer.complete", "transfer", transfer.getPublicId(), correlationId, "{}");
        outbox.emit("transfer.completed", "transfer", transfer.getPublicId(), correlationId, Map.of("transferId", transfer.getPublicId()));
        meterRegistry.counter("moneytransfer.transfer.outcome", "status", "completed").increment();
        return TransferResponse.from(transfer, correlationId);
    }

    private ResponseEntity<String> idempotent(String ownerId, String key, String requestHash, Supplier<TransferResponse> supplier) {
        return idempotencyRepository.findByOwnerIdAndIdempotencyKey(ownerId, key)
                .map(record -> replay(record, requestHash))
                .orElseGet(() -> {
                    TransferResponse response = supplier.get();
                    String body = writeJson(response);
                    idempotencyRepository.save(new IdempotencyRecord(ownerId, key, requestHash, body, HttpStatus.CREATED.value()));
                    return ResponseEntity.status(HttpStatus.CREATED).body(body);
                });
    }

    private ResponseEntity<String> replay(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            meterRegistry.counter("moneytransfer.idempotency", "result", "conflict").increment();
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for a different request.");
        }
        meterRegistry.counter("moneytransfer.idempotency", "result", "replay").increment();
        return ResponseEntity.status(record.getStatusCode()).body(record.getResponseBody());
    }

    private Transfer findOwnedTransfer(String transferId) {
        return transferRepository.findByOwnerIdAndPublicId(currentUser.ownerId(), transferId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "Transfer was not found.",
                        Map.of("transferId", transferId)));
    }

    private void validatePublicAccountIds(TransferRequest request) {
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER", "Source and destination accounts must be different.");
        }
        if (!request.sourceAccountId().startsWith("acct_") || !request.destinationAccountId().startsWith("acct_")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_ID", "Account IDs must use the acct_ public ID format.");
        }
    }

    private void validateAccounts(Account source, Account destination, String currency, BigDecimal amount) {
        if (source.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_ACCOUNT_NOT_ACTIVE", "Source account must be active.");
        }
        if (destination.getStatus() == AccountStatus.CLOSED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DESTINATION_ACCOUNT_CLOSED", "Destination account is closed.");
        }
        if (!source.getCurrency().equals(currency) || !destination.getCurrency().equals(currency)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENCY_MISMATCH", "Transfer currency must match both account currencies.");
        }
        if (source.getAvailableBalance().compareTo(amount) < 0) {
            meterRegistry.counter("moneytransfer.transfer.insufficient_funds", "currency", currency).increment();
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS", "The source account does not have enough available balance.",
                    Map.of("accountId", source.getPublicId(), "currency", currency));
        }
    }

    private ApiException notFound(String field, String accountId) {
        return new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account was not found.", Map.of(field, accountId));
    }

    private String requestHash(TransferRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(objectMapper.writeValueAsBytes(request)));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Could not hash request", e);
        }
    }

    private String writeJson(TransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize transfer response", e);
        }
    }

    private void recordLatency(long startNanos, String status) {
        Timer.builder("moneytransfer.transfer.latency")
                .tag("status", status)
                .register(meterRegistry)
                .record(Duration.ofNanos(System.nanoTime() - startNanos));
    }
}
