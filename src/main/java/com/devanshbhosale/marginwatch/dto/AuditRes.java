package com.devanshbhosale.marginwatch.dto;

import java.time.LocalDateTime;

public record AuditRes(Long id, String actorEmail, String action, String entityRef, String details,
        LocalDateTime createdAt) {
}
