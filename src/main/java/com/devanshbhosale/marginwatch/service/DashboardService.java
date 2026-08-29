package com.devanshbhosale.marginwatch.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devanshbhosale.marginwatch.domain.AuditLog;
import com.devanshbhosale.marginwatch.domain.PriceFileRow;
import com.devanshbhosale.marginwatch.domain.Product;
import com.devanshbhosale.marginwatch.domain.RowFlag;
import com.devanshbhosale.marginwatch.domain.RowStatus;
import com.devanshbhosale.marginwatch.dto.DashboardRes;
import com.devanshbhosale.marginwatch.repository.AuditLogRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRowRepository;
import com.devanshbhosale.marginwatch.repository.ProductRepository;

@Service
public class DashboardService {

    private final ProductRepository productRepository;
    private final PriceFileRowRepository rowRepository;
    private final PriceFileRepository fileRepository;
    private final AuditLogRepository auditRepository;

    public DashboardService(ProductRepository productRepository,
                            PriceFileRowRepository rowRepository,
                            PriceFileRepository fileRepository,
                            AuditLogRepository auditRepository) {
        this.productRepository = productRepository;
        this.rowRepository = rowRepository;
        this.fileRepository = fileRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional(readOnly = true)
    public DashboardRes load() {
        LocalDateTime now = LocalDateTime.now(FileService.IST);
        LocalDate today = now.toLocalDate();

        long pendingBreaches = rowRepository.countByStatusAndFlag(RowStatus.PENDING, RowFlag.BREACH);
        List<PriceFileRow> pendingBreachRows = rowRepository.findByStatusAndFlag(RowStatus.PENDING, RowFlag.BREACH);

        // marginAtRisk = Σ over PENDING BREACH rows of (selling − new) − (selling − old);
        // negative rupees = margin the breaches would burn.
        BigDecimal marginAtRisk = BigDecimal.ZERO;
        Map<String, Product> productsBySku = productsBySku(pendingBreachRows.stream()
                .map(PriceFileRow::getSku).collect(Collectors.toSet()));
        for (PriceFileRow row : pendingBreachRows) {
            Product product = row.getSku() == null ? null : productsBySku.get(key(row.getSku()));
            if (product == null) {
                continue;
            }
            BigDecimal selling = product.getSellingPrice();
            BigDecimal before = selling.subtract(row.getOldCost() == null ? BigDecimal.ZERO : row.getOldCost());
            BigDecimal after = selling.subtract(row.getNewCost() == null ? BigDecimal.ZERO : row.getNewCost());
            marginAtRisk = marginAtRisk.add(after.subtract(before));
        }
        marginAtRisk = marginAtRisk.setScale(2, RoundingMode.HALF_UP);

        // Applied rows in the last 14 IST days: one PRICE_APPLIED audit row per applied row.
        List<AuditLog> appliedRows = auditRepository.appliedSince(today.minusDays(13).atStartOfDay());
        Map<LocalDate, Long> byDay = appliedRows.stream()
                .collect(Collectors.groupingBy(a -> a.getCreatedAt().toLocalDate(), Collectors.counting()));
        List<DashboardRes.ChangePoint> changesPerDay = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            changesPerDay.add(new DashboardRes.ChangePoint(day.toString(), byDay.getOrDefault(day, 0L)));
        }

        Map<String, List<Product>> byCategory = productRepository.findAll().stream()
                .collect(Collectors.groupingBy(Product::getCategory, Collectors.toList()));
        List<DashboardRes.CategoryMargin> marginByCategory = byCategory.entrySet().stream()
                .map(entry -> new DashboardRes.CategoryMargin(entry.getKey(), avg(entry.getValue())))
                .sorted(Comparator.comparing(DashboardRes.CategoryMargin::category))
                .toList();

        List<PriceFileRow> breaches = rowRepository.findByFlag(RowFlag.BREACH);
        Map<String, Product> breachProducts = productsBySku(breaches.stream()
                .map(PriceFileRow::getSku).collect(Collectors.toSet()));
        Map<String, Long> heat = new LinkedHashMap<>();
        for (PriceFileRow row : breaches) {
            String vendorCode = row.getPriceFile().getVendor().getCode();
            Product product = row.getSku() == null ? null : breachProducts.get(key(row.getSku()));
            String category = product == null ? null : product.getCategory();
            heat.merge(vendorCode + "|" + category, 1L, Long::sum);
        }
        List<DashboardRes.HeatCell> heatmap = heat.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|", -1);
                    return new DashboardRes.HeatCell(parts[0], parts[1].isEmpty() ? null : parts[1], entry.getValue());
                })
                .sorted(Comparator.comparing(DashboardRes.HeatCell::vendorCode)
                        .thenComparing(cell -> cell.category() == null ? "" : cell.category()))
                .toList();

        List<DashboardRes.TopMover> topMovers = rowRepository.findByChangePctIsNotNull().stream()
                .sorted(Comparator.comparing((PriceFileRow r) -> r.getChangePct().abs()).reversed())
                .limit(8)
                .map(row -> new DashboardRes.TopMover(row.getSku(),
                        row.getPriceFile().getVendor().getCode(),
                        row.getOldCost(), row.getNewCost(), row.getChangePct(),
                        row.getFlag().name(), row.getStatus().name(),
                        row.getPriceFile().getId(), row.getPriceFile().getUploadedAt()))
                .toList();

        DashboardRes.Stats stats = new DashboardRes.Stats(
                productRepository.count(),
                rowRepository.countByStatus(RowStatus.PENDING),
                pendingBreaches,
                marginAtRisk,
                fileRepository.countByUploadedAtAfter(now.minusDays(30)));
        return new DashboardRes(stats, changesPerDay, marginByCategory, heatmap, topMovers);
    }

    private static BigDecimal avg(List<Product> products) {
        List<BigDecimal> margins = products.stream()
                .map(Product::marginPct)
                .filter(margin -> margin != null)
                .toList();
        if (margins.isEmpty()) {
            return null;
        }
        BigDecimal total = margins.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(margins.size()), 2, RoundingMode.HALF_UP);
    }

    private Map<String, Product> productsBySku(java.util.Set<String> skus) {
        Map<String, Product> bySku = new HashMap<>();
        if (skus.isEmpty()) {
            return bySku;
        }
        for (Product product : productRepository.findBySkuIn(skus)) {
            bySku.put(key(product.getSku()), product);
        }
        return bySku;
    }

    private static String key(String sku) {
        return sku.toUpperCase(Locale.ROOT);
    }
}
