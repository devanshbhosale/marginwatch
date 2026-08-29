package com.devanshbhosale.marginwatch.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/**
 * category null/blank means the global floor.
 */
public record PolicyReq(String category, @NotNull(message = "minMarginPct is required") BigDecimal minMarginPct) {
}
