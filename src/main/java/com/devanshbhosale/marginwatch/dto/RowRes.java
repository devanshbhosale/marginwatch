package com.devanshbhosale.marginwatch.dto;

import java.math.BigDecimal;

/**
 * productSellingPrice / policyFloorPct are joined from product + policy for the
 * diff screen; null when the row resolved to no product.
 */
public record RowRes(Long id, int rowNumber, String sku, String productName,
        BigDecimal oldCost, BigDecimal newCost, BigDecimal changePct, BigDecimal resultingMarginPct,
        String flag, String status, String rawRow, String note,
        BigDecimal productSellingPrice, BigDecimal policyFloorPct) {
}
