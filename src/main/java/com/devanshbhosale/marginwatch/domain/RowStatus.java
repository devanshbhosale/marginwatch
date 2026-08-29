package com.devanshbhosale.marginwatch.domain;

/**
 * Row lifecycle: PENDING until acted on. APPLIED moves the product cost and
 * writes audit rows in one transaction. SKIPPED is the operator declining a
 * row. NEW_SKU / NOT_FOUND / DUPLICATE rows can only ever be SKIPPED.
 */
public enum RowStatus {
    PENDING, APPLIED, SKIPPED
}
