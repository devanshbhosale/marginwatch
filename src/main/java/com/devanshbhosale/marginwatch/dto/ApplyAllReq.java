package com.devanshbhosale.marginwatch.dto;

/**
 * Body of POST /api/files/{id}/apply-all. When includeBreaches is true an
 * overrideReason is required and the caller must be ADMIN.
 */
public record ApplyAllReq(Boolean includeBreaches, String overrideReason) {
}
