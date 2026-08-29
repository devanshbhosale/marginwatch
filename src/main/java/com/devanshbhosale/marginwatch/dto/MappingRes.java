package com.devanshbhosale.marginwatch.dto;

import com.devanshbhosale.marginwatch.domain.FileColumnMapping.TargetField;

/**
 * One saved column mapping; used for both the PUT request body and responses.
 */
public record MappingRes(String sourceHeader, TargetField targetField) {
}
