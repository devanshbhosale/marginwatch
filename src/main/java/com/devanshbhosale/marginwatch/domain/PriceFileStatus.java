package com.devanshbhosale.marginwatch.domain;

/**
 * Price-file processing state machine: UPLOADED → PARSING → PARSED | FAILED.
 * A startup sweeper marks anything still PARSING/UPLOADED as FAILED so a
 * Render restart cannot leave files stuck mid-processing.
 */
public enum PriceFileStatus {
    UPLOADED, PARSING, PARSED, FAILED
}
