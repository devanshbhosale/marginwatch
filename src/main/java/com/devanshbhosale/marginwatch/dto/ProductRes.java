package com.devanshbhosale.marginwatch.dto;

import java.math.BigDecimal;

public record ProductRes(Long id, String sku, String vendorCode, String category, String name,
        BigDecimal currentCost, BigDecimal sellingPrice, BigDecimal marginPct) {
}
