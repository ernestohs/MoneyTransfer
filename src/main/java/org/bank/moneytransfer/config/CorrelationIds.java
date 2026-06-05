package org.bank.moneytransfer.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;

public final class CorrelationIds {
    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = "correlationId";

    private CorrelationIds() {
    }

    public static String newId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String current(HttpServletRequest request) {
        return Optional.ofNullable((String) request.getAttribute(ATTRIBUTE)).orElseGet(CorrelationIds::newId);
    }
}
