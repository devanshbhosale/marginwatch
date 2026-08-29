package com.devanshbhosale.marginwatch.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PriceReq(
        @NotNull(message = "Selling price is required")
        @DecimalMin(value = "0.00", message = "Selling price must be zero or positive") BigDecimal sellingPrice) {
}
