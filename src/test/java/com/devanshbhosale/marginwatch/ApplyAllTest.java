package com.devanshbhosale.marginwatch;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.devanshbhosale.marginwatch.domain.Product;
import com.devanshbhosale.marginwatch.domain.Vendor;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyAllTest extends IntegrationTestBase {

    private record Fixture(long fileId, Product ok1, Product ok2, Product breach) {
    }

    /** 2 OK rows + 1 BREACH row + 1 NEW_SKU row, all PENDING. */
    private Fixture fixture() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        Product ok1 = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");
        Product ok2 = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");
        Product breach = newProduct(vendor, unique("Cat"), "1000.00", "1100.00");

        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {ok1.getSku(), "Ok one", "5,050.00"},
                {ok2.getSku(), "Ok two", "5,100.00"},
                {breach.getSku(), "Breach", "1,060.00"},
                {"NEW-" + unique("N"), "New sku", "900.00"},
        });
        return new Fixture(fileId, ok1, ok2, breach);
    }

    @Test
    void applyAllAppliesOkRowsOnlyAndLeavesBreachesPending() throws Exception {
        Fixture fixture = fixture();
        MvcResult result = postJson("/api/files/" + fixture.fileId() + "/apply-all", analyst(),
                "{\"includeBreaches\":false}");
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = json(result);
        assertEquals(2, body.path("applied").asInt());
        assertEquals(0, body.path("breachApplied").asInt());
        // breach row + new-sku row were not applied
        assertEquals(2, body.path("skipped").asInt());

        assertEquals(0, productRepository.findById(fixture.ok1().getId()).orElseThrow()
                .getCurrentCost().compareTo(new BigDecimal("5050.00")));
        assertEquals(0, productRepository.findById(fixture.breach().getId()).orElseThrow()
                .getCurrentCost().compareTo(new BigDecimal("1000.00")));

        JsonNode breachRow = rowsOf(fixture.fileId()).get("content").get(2);
        assertEquals("BREACH", breachRow.path("flag").asText());
        assertEquals("PENDING", breachRow.path("status").asText());
    }

    @Test
    void includeBreachesWithoutReasonIs422() throws Exception {
        Fixture fixture = fixture();
        MvcResult result = postJson("/api/files/" + fixture.fileId() + "/apply-all", admin(),
                "{\"includeBreaches\":true}");
        assertEquals(422, result.getResponse().getStatus());
        assertTrue(json(result).path("error").asText().contains("override reason"));
    }

    @Test
    void includeBreachesAsAnalystIs403() throws Exception {
        Fixture fixture = fixture();
        MvcResult result = postJson("/api/files/" + fixture.fileId() + "/apply-all", analyst(),
                "{\"includeBreaches\":true,\"overrideReason\":\"not an admin\"}");
        assertEquals(403, result.getResponse().getStatus());
    }

    @Test
    void includeBreachesAsAdminAppliesEveryRowWithPerRowAudit() throws Exception {
        Fixture fixture = fixture();
        MvcResult result = postJson("/api/files/" + fixture.fileId() + "/apply-all", admin(),
                "{\"includeBreaches\":true,\"overrideReason\":\"Finance approved batch\"}");
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = json(result);
        assertEquals(2, body.path("applied").asInt());
        assertEquals(1, body.path("breachApplied").asInt());
        assertEquals(1, body.path("skipped").asInt());

        assertEquals(0, productRepository.findById(fixture.breach().getId()).orElseThrow()
                .getCurrentCost().compareTo(new BigDecimal("1060.00")));

        // one audit row per applied row, sharing the reason
        java.util.Set<String> appliedRefs = new java.util.HashSet<>();
        for (JsonNode row : rowsOf(fixture.fileId()).get("content")) {
            if ("APPLIED".equals(row.path("status").asText())) {
                appliedRefs.add("PRICE_FILE_ROW:" + row.path("id").asLong());
            }
        }
        assertEquals(3, appliedRefs.size());

        MvcResult audit = getJson("/api/audit?action=PRICE_APPLIED&size=50", admin());
        long mine = 0;
        long withReason = 0;
        for (JsonNode entry : json(audit).get("content")) {
            if (appliedRefs.contains(entry.path("entityRef").asText())) {
                mine++;
                if (entry.path("details").asText().contains("override reason: Finance approved batch")) {
                    withReason++;
                }
            }
        }
        assertEquals(3, mine);
        assertEquals(1, withReason);
    }
}
