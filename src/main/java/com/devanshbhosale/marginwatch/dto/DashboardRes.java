package com.devanshbhosale.marginwatch.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardRes(Stats stats, List<ChangePoint> changesPerDay, List<CategoryMargin> marginByCategory,
        List<HeatCell> heatmap, List<TopMover> topMovers) {

    public record Stats(long skusMonitored, long pendingRows, long pendingBreaches, BigDecimal marginAtRisk,
            long filesLast30d) {
    }

    /** 14 IST days, zero-filled, date as ISO "2026-08-29". */
    public record ChangePoint(String date, long applied) {
    }

    public record CategoryMargin(String category, BigDecimal avgMarginPct) {
    }

    /** All BREACH rows grouped vendor (from the file) x category (from the product). */
    public record HeatCell(String vendorCode, String category, long breaches) {
    }

    public record TopMover(String sku, String vendorCode, BigDecimal oldCost, BigDecimal newCost,
            BigDecimal changePct, String flag, String status, Long fileId, LocalDateTime fileUploadedAt) {
    }
}
