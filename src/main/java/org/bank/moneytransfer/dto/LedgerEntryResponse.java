package org.bank.moneytransfer.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.bank.moneytransfer.domain.LedgerDirection;
import org.bank.moneytransfer.domain.LedgerEntry;

public record LedgerEntryResponse(
        String transactionId,
        String transferId,
        String accountId,
        LedgerDirection direction,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        String description,
        OffsetDateTime createdAt
) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getPublicId(),
                entry.getTransfer().getPublicId(),
                entry.getAccount().getPublicId(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getBalanceAfter(),
                entry.getDescription(),
                entry.getCreatedAt()
        );
    }
}
