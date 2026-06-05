package org.bank.moneytransfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull @DecimalMin(value = "0.00") BigDecimal initialBalance
) {
}
