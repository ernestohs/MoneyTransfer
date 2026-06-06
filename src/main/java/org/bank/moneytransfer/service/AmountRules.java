package org.bank.moneytransfer.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.bank.moneytransfer.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AmountRules {
    private final BigDecimal minAmount;
    private final BigDecimal maxAmount;

    public AmountRules(@Value("${moneytransfer.transfer.min-amount}") BigDecimal minAmount,
                       @Value("${moneytransfer.transfer.max-amount}") BigDecimal maxAmount) {
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    public BigDecimal normalize(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Transfer amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT_PRECISION", "Transfer amount supports at most two decimal places.");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.compareTo(minAmount) < 0 || normalized.compareTo(maxAmount) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRANSFER_LIMIT_EXCEEDED", "Transfer amount is outside allowed limits.",
                    Map.of("minAmount", minAmount, "maxAmount", maxAmount));
        }
        return normalized;
    }
}
