package org.bank.moneytransfer.dto;

import java.math.BigDecimal;

public record TransferQuoteResponse(
        BigDecimal amount,
        String currency,
        BigDecimal fee,
        BigDecimal totalDebit,
        String correlationId
) {
}
