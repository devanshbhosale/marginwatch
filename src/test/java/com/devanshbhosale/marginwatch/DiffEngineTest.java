package com.devanshbhosale.marginwatch;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.devanshbhosale.marginwatch.domain.Product;
import com.devanshbhosale.marginwatch.domain.Vendor;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Diff-engine rules exercised end to end through uploads: loss rows, floor
 * precedence and the zero-cost edges.
 */
class DiffEngineTest extends IntegrationTestBase {

    private Vendor mappedVendor() {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        return vendor;
    }

    @Test
    void lossRowIsBreachEvenWhenNoFloorRuleApplies() throws Exception {
        Vendor vendor = mappedVendor();
        // selling 0 => margin is null (no floor rule can apply); cost hike above
        // the selling price must still be a loss breach.
        Product product = newProduct(vendor, unique("Cat"), "100.00", "0.00");

        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {product.getSku(), "Loss", "13,900.00"},
        });
        JsonNode row = rowsOf(fileId).get("content").get(0);
        assertEquals("BREACH", row.path("flag").asText());
        assertEquals("", row.path("resultingMarginPct").asText());
    }

    @Test
    void zeroOldCostGivesNullChangePctWithoutBreaking() throws Exception {
        Vendor vendor = mappedVendor();
        Product product = newProduct(vendor, unique("Cat"), "0.00", "1000.00");

        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {product.getSku(), "First ever cost", "500.00"},
        });
        JsonNode row = rowsOf(fileId).get("content").get(0);
        assertEquals("OK", row.path("flag").asText());
        assertEquals("", row.path("changePct").asText());
        assertEquals(0, new BigDecimal("50.00").compareTo(new BigDecimal(row.path("resultingMarginPct").asText())));
    }

    @Test
    void categoryFloorOverridesGlobalFloor() throws Exception {
        Vendor vendor = mappedVendor();
        // Laptops floor is 10 (seeded); global floor 8 would call 9.5% fine.
        Product product = newProduct(vendor, "Laptops", "1000.00", "1105.00");

        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {product.getSku(), "Laptop", "1,000.00"},
        });
        JsonNode row = rowsOf(fileId).get("content").get(0);
        assertEquals("BREACH", row.path("flag").asText());
        assertEquals(0, new BigDecimal("9.50").compareTo(new BigDecimal(row.path("resultingMarginPct").asText())));
        assertEquals(0, new BigDecimal("10.00").compareTo(new BigDecimal(row.path("policyFloorPct").asText())));
    }

    @Test
    void globalFloorAppliesWhenCategoryHasNoPolicy() throws Exception {
        Vendor vendor = mappedVendor();
        Product breaching = newProduct(vendor, unique("Cat"), "930.00", "1000.00");
        Product fine = newProduct(vendor, unique("Cat"), "910.00", "1000.00");

        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {breaching.getSku(), "Below floor", "930.00"},
                {fine.getSku(), "Above floor", "910.00"},
        });
        JsonNode rows = rowsOf(fileId);
        assertEquals("BREACH", rows.get("content").get(0).path("flag").asText());
        assertEquals("OK", rows.get("content").get(1).path("flag").asText());
    }
}
