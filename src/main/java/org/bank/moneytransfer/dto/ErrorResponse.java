package org.bank.moneytransfer.dto;

import java.util.Map;

public record ErrorResponse(
        String errorCode,
        String message,
        String correlationId,
        Map<String, Object> details
) {
}
