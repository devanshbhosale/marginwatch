package com.devanshbhosale.marginwatch.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * FileRes fields flattened plus per-flag / per-status row counts (zero-filled
 * for every enum constant).
 */
public record FileDetailRes(Long id, String vendorCode, String filename, String status, LocalDateTime uploadedAt,
        String uploadedByName, int totalRows, int updatedRows, int errorRows, String errorReport,
        LocalDateTime processedAt, Map<String, Long> flagCounts, Map<String, Long> statusCounts) {
}
