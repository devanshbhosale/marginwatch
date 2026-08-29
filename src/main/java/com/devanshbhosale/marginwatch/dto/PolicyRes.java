package com.devanshbhosale.marginwatch.dto;

import java.math.BigDecimal;

public record PolicyRes(Long id, String category, BigDecimal minMarginPct) {
}
