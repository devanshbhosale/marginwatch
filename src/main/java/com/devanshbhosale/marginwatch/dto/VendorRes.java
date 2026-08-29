package com.devanshbhosale.marginwatch.dto;

/**
 * mapped = the vendor has both SKU and COST_PRICE mapped, i.e. uploads will parse.
 */
public record VendorRes(String code, String name, boolean mapped, long mappingCount) {
}
