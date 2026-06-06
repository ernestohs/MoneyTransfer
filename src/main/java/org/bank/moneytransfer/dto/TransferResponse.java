package org.bank.moneytransfer.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.bank.moneytransfer.domain.Transfer;
import org.bank.moneytransfer.domain.TransferStatus;

public record TransferResponse(
        String transferId,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        String description,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String correlationId
) {
    public static TransferResponse from(Transfer transfer, String correlationId) {
        return new TransferResponse(
                transfer.getPublicId(),
                transfer.getSourceAccount().getPublicId(),
                transfer.getDestinationAccount().getPublicId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getDescription(),
                transfer.getFailureReason(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt(),
                correlationId
        );
    }
}
