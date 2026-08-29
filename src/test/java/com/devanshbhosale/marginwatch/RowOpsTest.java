package com.devanshbhosale.marginwatch;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.devanshbhosale.marginwatch.domain.Product;
import com.devanshbhosale.marginwatch.domain.Vendor;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class RowOpsTest extends IntegrationTestBase {

    private Vendor mappedVendor() {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        return vendor;
    }

    /** A row that breaches the global 8% floor: 9.09% today, 3.64% after the hike. */
    private record Fixture(long fileId, long okRowId, long breachRowId, long newSkuRowId,
                           Product okProduct, Product breachProduct) {
    }

    private Fixture fixtureWithAllFlags() throws Exception {
        Vendor vendor = mappedVendor();
        Product okProduct = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");
        Product breachProduct = newProduct(vendor, unique("Cat"), "1000.00", "1100.00");

        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {okProduct.getSku(), "Ok row", "5,200.00"},
                {breachProduct.getSku(), "Breach row", "1,060.00"},
                {"FRESH-" + unique("N"), "New sku row", "900.00"},
        });
        JsonNode rows = rowsOf(fileId);
        return new Fixture(fileId,
                rowBySku(rows, okProduct.getSku()).get("id").asLong(),
                rowBySku(rows, breachProduct.getSku()).get("id").asLong(),
                rows.get("content").get(2).get("id").asLong(),
                okProduct, breachProduct);
    }

    @Test
    void applyOkRowMovesProductCostAndWritesAudit() throws Exception {
        Fixture fixture = fixtureWithAllFlags();
        MvcResult result = postJson("/api/rows/" + fixture.okRowId() + "/apply", analyst(), "{}");
        assertEquals(200, result.getResponse().getStatus());
        JsonNode row = json(result);
        assertEquals("APPLIED", row.path("status").asText());
        assertEquals("OK", row.path("flag").asText());

        Product moved = productRepository.findById(fixture.okProduct().getId()).orElseThrow();
        assertEquals(0, moved.getCurrentCost().compareTo(new BigDecimal("5200.00")));

        MvcResult audit = getJson("/api/audit?action=PRICE_APPLIED&size=5", analyst());
        JsonNode entry = json(audit).get("content").get(0);
        assertEquals("PRICE_APPLIED", entry.path("action").asText());
        assertTrue(entry.path("entityRef").asText().startsWith("PRICE_FILE_ROW:"));
        assertTrue(entry.path("details").asText().contains("cost 5000.00 → 5200.00"));
        assertTrue(entry.path("details").asText().contains("resulting margin 13.33%"));
    }

    @Test
    void applyingTwiceReturns409() throws Exception {
        Fixture fixture = fixtureWithAllFlags();
        assertEquals(200, postJson("/api/rows/" + fixture.okRowId() + "/apply", analyst(), "{}")
                .getResponse().getStatus());
        MvcResult second = postJson("/api/rows/" + fixture.okRowId() + "/apply", analyst(), "{}");
        assertEquals(409, second.getResponse().getStatus());
        assertEquals("Row is no longer pending", json(second).path("error").asText());
    }

    @Test
    void applyingNewSkuRowReturns409SkipOnly() throws Exception {
        Fixture fixture = fixtureWithAllFlags();
        MvcResult result = postJson("/api/rows/" + fixture.newSkuRowId() + "/apply", analyst(), "{}");
        assertEquals(409, result.getResponse().getStatus());
        assertTrue(json(result).path("error").asText().contains("not applicable — skip only"));
    }

    @Test
    void breachApplyWithoutReasonIs422() throws Exception {
        Fixture fixture = fixtureWithAllFlags();
        MvcResult result = postJson("/api/rows/" + fixture.breachRowId() + "/apply", admin(), "{}");
        assertEquals(422, result.getResponse().getStatus());
        assertTrue(json(result).path("error").asText().contains("override reason"));
    }

    @Test
    void breachApplyWithReasonAsAdminMovesCost() throws Exception {
        Fixture fixture = fixtureWithAllFlags();
        MvcResult result = postJson("/api/rows/" + fixture.breachRowId() + "/apply", admin(),
                "{\"overrideReason\":\"Board approved the hike\"}");
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("APPLIED", json(result).path("status").asText());

        Product product = productRepository.findById(fixture.breachProduct().getId()).orElseThrow();
        assertEquals(0, product.getCurrentCost().compareTo(new BigDecimal("1060.00")));

        MvcResult audit = getJson("/api/audit?action=PRICE_APPLIED&size=5", admin());
        assertTrue(json(audit).get("content").get(0).path("details").asText()
                .contains("override reason: Board approved the hike"));
    }

    @Test
    void breachApplyAsAnalystIs403() throws Exception {
        Fixture fixture = fixtureWithAllFlags();
        MvcResult result = postJson("/api/rows/" + fixture.breachRowId() + "/apply", analyst(),
                "{\"overrideReason\":\"I am not an admin\"}");
        assertEquals(403, result.getResponse().getStatus());
    }

    @Test
    void skipRowThenSkipAgainIs409() throws Exception {
        Fixture fixture = fixtureWithAllFlags();
        MvcResult result = postJson("/api/rows/" + fixture.okRowId() + "/skip", analyst(), "");
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("SKIPPED", json(result).path("status").asText());
        MvcResult second = postJson("/api/rows/" + fixture.okRowId() + "/skip", analyst(), "");
        assertEquals(409, second.getResponse().getStatus());
        assertEquals("Row is no longer pending", json(second).path("error").asText());
    }

    @Test
    void unknownRowIs404() throws Exception {
        MvcResult result = postJson("/api/rows/99999999/apply", analyst(), "{}");
        assertEquals(404, result.getResponse().getStatus());
    }
}
