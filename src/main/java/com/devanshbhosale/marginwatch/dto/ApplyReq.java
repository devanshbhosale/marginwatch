package com.devanshbhosale.marginwatch.dto;

/**
 * Body of POST /api/rows/{id}/apply. Required (non-blank) when applying a BREACH.
 */
public record ApplyReq(String overrideReason) {
}
