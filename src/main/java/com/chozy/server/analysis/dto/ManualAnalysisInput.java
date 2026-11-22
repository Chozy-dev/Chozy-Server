package com.chozy.server.analysis.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ManualAnalysisInput(
    @NotBlank @Size(max = 500) String productName,
    @NotNull @Positive @Digits(integer = 16, fraction = 4) BigDecimal wholesalePrice,
    @NotNull @PositiveOrZero @Digits(integer = 16, fraction = 4) BigDecimal shippingFee,
    @NotNull Boolean hasPricePolicy,
    @Positive @Digits(integer = 16, fraction = 4) BigDecimal minimumSellingPrice,
    @Size(max = 500) String keyword) {
}
