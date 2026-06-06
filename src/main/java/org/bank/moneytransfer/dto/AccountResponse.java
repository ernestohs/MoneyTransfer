package org.bank.moneytransfer.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.bank.moneytransfer.domain.Account;
import org.bank.moneytransfer.domain.AccountStatus;

public record AccountResponse(
        String accountId,
        String ownerId,
        String currency,
        AccountStatus status,
        BigDecimal availableBalance,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String correlationId
) {
    public static AccountResponse from(Account account, String correlationId) {
        return new AccountResponse(
                account.getPublicId(),
                account.getOwnerId(),
                account.getCurrency(),
                account.getStatus(),
                account.getAvailableBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                correlationId
        );
    }
}
