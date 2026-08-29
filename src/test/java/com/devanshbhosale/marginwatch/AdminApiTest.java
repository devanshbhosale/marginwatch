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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class AdminApiTest extends IntegrationTestBase {

    @Test
    void productsListIsPagedAndFilterable() throws Exception {
        Vendor vendor = newVendor();
        String category = unique("Cat");
        Product a = newProduct(vendor, category, "100.00", "120.00");
        Product b = newProduct(vendor, category, "200.00", "240.00");
        newProduct(vendor, unique("Other"), "300.00", "360.00");

        MvcResult byVendor = getJson("/api/products?vendor=" + vendor.getCode() + "&size=50", viewer());
        JsonNode page = json(byVendor);
        assertEquals(3, page.path("totalElements").asLong());

        MvcResult byCategory = getJson("/api/products?category=" + category + "&size=50", viewer());
        assertEquals(2, json(byCategory).path("totalElements").asLong());

        MvcResult byQuery = getJson("/api/products?q=" + a.getSku(), viewer());
        JsonNode search = json(byQuery);
        assertEquals(1, search.path("totalElements").asLong());
        JsonNode product = search.get("content").get(0);
        assertEquals(a.getSku(), product.path("sku").asText());
        assertEquals(vendor.getCode(), product.path("vendorCode").asText());
        assertTrue(product.has("currentCost") && product.has("sellingPrice") && product.has("marginPct"));
        assertEquals(0, new BigDecimal("16.67").compareTo(new BigDecimal(product.path("marginPct").asText())));
    }

    @Test
    void priceEditAsAdminIsAudited() throws Exception {
        Vendor vendor = newVendor();
        Product product = newProduct(vendor, unique("Cat"), "100.00", "150.00");

        MvcResult result = mockMvc.perform(put("/api/products/" + product.getId() + "/price")
                        .header("Authorization", bearer(admin()))
                        .contentType("application/json")
                        .content("{\"sellingPrice\": 9999.00}"))
                .andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = json(result);
        assertEquals(0, new BigDecimal("9999.00").compareTo(new BigDecimal(body.path("sellingPrice").asText())));
        assertEquals(0, new BigDecimal("99.00").compareTo(new BigDecimal(body.path("marginPct").asText())));

        MvcResult audit = getJson("/api/audit?action=PRICE_EDIT&size=5", admin());
        JsonNode entry = json(audit).get("content").get(0);
        assertEquals("PRODUCT:" + product.getId(), entry.path("entityRef").asText());
        assertTrue(entry.path("details").asText().contains("150.00 → 9999.00"));
    }

    @Test
    void priceEditAsAnalystIs403() throws Exception {
        Vendor vendor = newVendor();
        Product product = newProduct(vendor, unique("Cat"), "100.00", "150.00");
        MvcResult result = mockMvc.perform(put("/api/products/" + product.getId() + "/price")
                        .header("Authorization", bearer(analyst()))
                        .contentType("application/json")
                        .content("{\"sellingPrice\": 9999.00}"))
                .andReturn();
        assertEquals(403, result.getResponse().getStatus());
    }

    @Test
    void policiesAdminCrudRoundTrip() throws Exception {
        String category = unique("Pol");
        MvcResult created = mockMvc.perform(post("/api/policies")
                        .header("Authorization", bearer(admin()))
                        .contentType("application/json")
                        .content("{\"category\":\"" + category + "\",\"minMarginPct\":9.50}"))
                .andReturn();
        assertEquals(201, created.getResponse().getStatus());
        long id = json(created).path("id").asLong();
        assertEquals(0, new BigDecimal("9.50").compareTo(new BigDecimal(json(created).path("minMarginPct").asText())));

        MvcResult list = getJson("/api/policies", viewer());
        boolean found = false;
        for (JsonNode policy : json(list)) {
            if (policy.path("id").asLong() == id) {
                found = true;
            }
        }
        assertTrue(found);

        MvcResult updated = mockMvc.perform(put("/api/policies/" + id)
                        .header("Authorization", bearer(admin()))
                        .contentType("application/json")
                        .content("{\"category\":\"" + category + "\",\"minMarginPct\":11.00}"))
                .andReturn();
        assertEquals(200, updated.getResponse().getStatus());
        assertEquals(0, new BigDecimal("11.00").compareTo(new BigDecimal(json(updated).path("minMarginPct").asText())));

        MvcResult audit = getJson("/api/audit?action=POLICY_UPDATED&size=5", admin());
        assertTrue(json(audit).get("content").get(0).path("entityRef").asText()
                .equals("MARGIN_POLICY:" + id));
    }

    @Test
    void duplicateCategoryPolicyIs409() throws Exception {
        String category = unique("Pol");
        MvcResult first = mockMvc.perform(post("/api/policies")
                        .header("Authorization", bearer(admin()))
                        .contentType("application/json")
                        .content("{\"category\":\"" + category + "\",\"minMarginPct\":9.50}"))
                .andReturn();
        assertEquals(201, first.getResponse().getStatus());

        MvcResult second = mockMvc.perform(post("/api/policies")
                        .header("Authorization", bearer(admin()))
                        .contentType("application/json")
                        .content("{\"category\":\"" + category + "\",\"minMarginPct\":7.00}"))
                .andReturn();
        assertEquals(409, second.getResponse().getStatus());
    }

    @Test
    void secondGlobalPolicyIs409() throws Exception {
        MvcResult second = mockMvc.perform(post("/api/policies")
                        .header("Authorization", bearer(admin()))
                        .contentType("application/json")
                        .content("{\"minMarginPct\":5.00}"))
                .andReturn();
        assertEquals(409, second.getResponse().getStatus());
        assertTrue(json(second).path("error").asText().contains("global policy already exists"));
    }

    @Test
    void policyWritesAsAnalystAre403() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/policies")
                        .header("Authorization", bearer(analyst()))
                        .contentType("application/json")
                        .content("{\"category\":\"" + unique("Pol") + "\",\"minMarginPct\":9.50}"))
                .andReturn();
        assertEquals(403, result.getResponse().getStatus());
    }

    @Test
    void auditListIsFilterableAndNewestFirst() throws Exception {
        Vendor vendor = newVendor();
        Product one = newProduct(vendor, unique("Cat"), "100.00", "150.00");
        Product two = newProduct(vendor, unique("Cat"), "100.00", "150.00");
        mockMvc.perform(put("/api/products/" + one.getId() + "/price")
                        .header("Authorization", bearer(admin())).contentType("application/json")
                        .content("{\"sellingPrice\": 111.00}")).andReturn();
        mockMvc.perform(put("/api/products/" + two.getId() + "/price")
                        .header("Authorization", bearer(admin())).contentType("application/json")
                        .content("{\"sellingPrice\": 222.00}")).andReturn();

        MvcResult byAction = getJson("/api/audit?action=PRICE_EDIT&size=20", viewer());
        JsonNode content = json(byAction).get("content");
        assertTrue(content.size() >= 2);
        for (JsonNode entry : content) {
            assertEquals("PRICE_EDIT", entry.path("action").asText());
        }
        String newest = content.get(0).path("createdAt").asText();
        String secondNewest = content.get(1).path("createdAt").asText();
        assertTrue(newest.compareTo(secondNewest) >= 0, "audit must be newest first");

        MvcResult byActor = getJson("/api/audit?actor=priya.nair&size=20", viewer());
        for (JsonNode entry : json(byActor).get("content")) {
            assertTrue(entry.path("actorEmail").asText().contains("priya.nair"));
        }
    }

    @Test
    void metaReturnsCategoriesAndVendorCodes() throws Exception {
        MvcResult result = getJson("/api/products/meta", viewer());
        JsonNode body = json(result);
        assertTrue(body.has("categories") && body.has("vendors"));
        assertTrue(body.get("categories").toString().contains("Networking"));
        assertTrue(body.get("vendors").toString().contains("CISCO"));
        assertTrue(body.get("vendors").toString().contains("LENOVO"));
    }
}
