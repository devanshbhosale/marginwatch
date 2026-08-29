package com.devanshbhosale.marginwatch.dto;

import java.time.LocalDateTime;

public record FileRes(Long id, String vendorCode, String filename, String status, LocalDateTime uploadedAt,
        String uploadedByName, int totalRows, int updatedRows, int errorRows, String errorReport,
        LocalDateTime processedAt) {
}
