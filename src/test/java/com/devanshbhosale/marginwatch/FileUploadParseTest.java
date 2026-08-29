package com.devanshbhosale.marginwatch;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.devanshbhosale.marginwatch.domain.Product;
import com.devanshbhosale.marginwatch.domain.Vendor;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUploadParseTest extends IntegrationTestBase {

    @Test
    void csvUploadParsesAndCleansRupeeAndCommas() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        Product p1 = newProduct(vendor, unique("Cat"), "10000.00", "14000.00");
        Product p2 = newProduct(vendor, unique("Cat"), "8000.00", "12000.00");
        Product p3 = newProduct(vendor, unique("Cat"), "100000.00", "130000.00");

        MvcResult result = upload(analyst(), vendor.getCode(), "upload.csv", csv(new String[][] {
                {"SKU", "Description", "Cost Price"},
                {p1.getSku(), "First Widget", "\u20B9 12,500.00"},
                {p2.getSku(), "Second Widget", "Rs. 9,999.50"},
                {p3.getSku(), "Third Widget", "INR 1,10,000"},
        }).getBytes(StandardCharsets.UTF_8));

        assertEquals(201, result.getResponse().getStatus());
        JsonNode file = json(result);
        assertEquals("PARSED", file.path("status").asText());
        assertEquals(3, file.path("totalRows").asInt());
        assertEquals(3, file.path("updatedRows").asInt());
        assertEquals(0, file.path("errorRows").asInt());
        assertEquals(vendor.getCode(), file.path("vendorCode").asText());
        assertEquals("Rahul Mehta", file.path("uploadedByName").asText());
        assertNotNull(file.path("processedAt").asText());

        JsonNode rows = rowsOf(file.get("id").asLong());
        JsonNode first = rowBySku(rows, p1.getSku());
        assertEquals("OK", first.path("flag").asText());
        assertEquals(0, new BigDecimal("12500.00").compareTo(new BigDecimal(first.path("newCost").asText())));
        assertEquals(0, new BigDecimal("25.00").compareTo(new BigDecimal(first.path("changePct").asText())));
        assertEquals(0, new BigDecimal("10.71").compareTo(new BigDecimal(first.path("resultingMarginPct").asText())));
        assertEquals(0, new BigDecimal("14000.00").compareTo(new BigDecimal(first.path("productSellingPrice").asText())));
        assertEquals(0, new BigDecimal("8.00").compareTo(new BigDecimal(first.path("policyFloorPct").asText())));

        JsonNode second = rowBySku(rows, p2.getSku());
        assertEquals(0, new BigDecimal("9999.50").compareTo(new BigDecimal(second.path("newCost").asText())));
        JsonNode third = rowBySku(rows, p3.getSku());
        assertEquals(0, new BigDecimal("110000").compareTo(new BigDecimal(third.path("newCost").asText())));
    }

    @Test
    void xlsxUploadParsesVendorHeaders() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "HP SKU", "SKU", "Product Name", "DESCRIPTION", "Partner Cost", "COST_PRICE");
        Product product = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");

        MvcResult result = upload(analyst(), vendor.getCode(), "upload.xlsx",
                xlsx(new String[] {"HP SKU", "Product Name", "Partner Cost"},
                        new String[][] {{product.getSku(), "HP Widget X", "5,500.00"}}));
        assertEquals(201, result.getResponse().getStatus());
        JsonNode file = json(result);
        assertEquals("PARSED", file.path("status").asText());
        assertEquals(1, file.path("totalRows").asInt());

        JsonNode row = rowBySku(rowsOf(file.get("id").asLong()), product.getSku());
        assertEquals("OK", row.path("flag").asText());
        assertEquals(0, new BigDecimal("5500.00").compareTo(new BigDecimal(row.path("newCost").asText())));
        assertEquals("HP Widget X", row.path("productName").asText());
        assertTrue(row.path("rawRow").asText().contains("HP Widget X"));
    }

    @Test
    void unparseableCostBecomesNotFound() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        Product ok1 = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");
        Product ok2 = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");
        Product bad = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");

        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {ok1.getSku(), "Fine", "5,200.00"},
                {bad.getSku(), "Broken", "N/A"},
                {ok2.getSku(), "Also fine", "5,300.00"},
        });
        JsonNode row = rowBySku(rowsOf(fileId), bad.getSku());
        assertEquals("NOT_FOUND", row.path("flag").asText());
        assertEquals("Cost not numeric", row.path("note").asText());
        assertTrue(row.path("oldCost").isMissingNode());
        JsonNode file = json(getJson("/api/files/" + fileId, admin()));
        assertEquals(1, file.path("errorRows").asInt());
    }

    @Test
    void blankSkuBecomesNotFound() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        Product product = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");

        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {product.getSku(), "Fine", "5,200.00"},
                {"", "No SKU", "5,200.00"},
        });
        JsonNode file = json(getJson("/api/files/" + fileId, admin()));
        assertEquals("PARSED", file.path("status").asText());
        JsonNode missing = rowsOf(fileId).get("content").get(1);
        assertEquals("NOT_FOUND", missing.path("flag").asText());
        assertEquals("SKU missing", missing.path("note").asText());
        assertEquals("", missing.path("sku").asText());
    }

    @Test
    void duplicateSkuSecondRowFlaggedAndFirstWins() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        Product product = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");

        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {product.getSku(), "First", "5,200.00"},
                {product.getSku(), "Second", "5,300.00"},
        });
        JsonNode rows = rowsOf(fileId);
        JsonNode first = rows.get("content").get(0);
        JsonNode second = rows.get("content").get(1);
        assertEquals("OK", first.path("flag").asText());
        assertEquals("DUPLICATE", second.path("flag").asText());
        assertEquals("SKU already seen earlier in this file", second.path("note").asText());
        assertTrue(second.path("changePct").isMissingNode());
        JsonNode file = json(getJson("/api/files/" + fileId, admin()));
        assertEquals(1, file.path("updatedRows").asInt());
        assertEquals(1, file.path("flagCounts").path("DUPLICATE").asLong());
    }

    @Test
    void newSkuFlaggedAndNotApplicable() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        Product product = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");

        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {product.getSku(), "Known", "5,200.00"},
                {"BRAND-NEW-" + unique("X"), "Unknown chip", "9,000.00"},
        });
        JsonNode rows = rowsOf(fileId);
        JsonNode fresh = rows.get("content").get(1);
        assertEquals("NEW_SKU", fresh.path("flag").asText());
        assertTrue(fresh.path("newCost").asText().contains("9000"));
        assertEquals("", fresh.path("productSellingPrice").asText());
        assertEquals("", fresh.path("policyFloorPct").asText());
        JsonNode file = json(getJson("/api/files/" + fileId, admin()));
        assertEquals(1, file.path("flagCounts").path("NEW_SKU").asLong());
        assertEquals(0, file.path("errorRows").asInt());
    }

    @Test
    void skuOfAnotherVendorIsNotFound() throws Exception {
        Vendor vendorA = newVendor();
        Product productA = newProduct(vendorA, unique("Cat"), "5000.00", "6000.00");

        Vendor vendorB = newVendor();
        mapVendor(vendorB, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        Product productB = newProduct(vendorB, unique("Cat"), "5000.00", "6000.00");

        long fileId = uploadCsv(analyst(), vendorB.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {productB.getSku(), "Own", "5,200.00"},
                {productA.getSku(), "Foreign", "5,200.00"},
        });
        JsonNode row = rowBySku(rowsOf(fileId), productA.getSku());
        assertEquals("NOT_FOUND", row.path("flag").asText());
        assertEquals("SKU belongs to another vendor", row.path("note").asText());
    }

    @Test
    void stalenessGuardFailsFileAndPersistsNoRows() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        Product product = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");

        MvcResult result = upload(analyst(), vendor.getCode(), "stale.csv", csv(new String[][] {
                {"SKU", "Description", "Cost Price"},
                {"WRONG-HEADER-SKU-A", "Broken", "N/A"},
                {"WRONG-HEADER-SKU-B", "Broken", "N/A"},
                {product.getSku(), "Fine", "5,200.00"},
        }).getBytes(StandardCharsets.UTF_8));
        assertEquals(201, result.getResponse().getStatus());
        JsonNode file = json(result);
        assertEquals("FAILED", file.path("status").asText());
        assertTrue(file.path("errorReport").asText()
                .contains("Over 50% of rows could not be resolved"));
        assertEquals(0, file.path("totalRows").asInt());
        assertEquals(0, rowsOf(file.get("id").asLong()).path("totalElements").asLong());
    }

    @Test
    void tooManyRowsFailsTheFile() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");

        StringBuilder content = new StringBuilder("SKU,Description,Cost Price\r\n");
        for (int i = 0; i < 5001; i++) {
            content.append("SKU-").append(i).append(",Row,Cost\r\n");
        }
        MvcResult result = upload(analyst(), vendor.getCode(), "huge.csv",
                content.toString().getBytes(StandardCharsets.UTF_8));
        JsonNode file = json(result);
        assertEquals("FAILED", file.path("status").asText());
        assertTrue(file.path("errorReport").asText().contains("5000"));
    }

    @Test
    void unsupportedFileTypeFailsTheFile() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        MvcResult result = upload(analyst(), vendor.getCode(), "prices.txt",
                "hello".getBytes(StandardCharsets.UTF_8));
        JsonNode file = json(result);
        assertEquals("FAILED", file.path("status").asText());
        assertTrue(file.path("errorReport").asText().contains("Unsupported file type"));
    }

    @Test
    void uploadWithoutMappingIs422AndCreatesNoFile() throws Exception {
        long filesBefore = fileRepository.count();
        MvcResult result = upload(analyst(), "LENOVO", "lenovo.csv",
                "SKU,Description,Cost\r\nX,Y,1\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(422, result.getResponse().getStatus());
        assertEquals("No column mapping saved for LENOVO — map the vendor's columns first",
                json(result).path("error").asText());
        assertEquals(filesBefore, fileRepository.count());
    }

    @Test
    void mappingPutThenUploadSucceeds() throws Exception {
        Vendor vendor = newVendor();
        MvcResult first = upload(analyst(), vendor.getCode(), "v.csv",
                "SKU,Description,Cost Price\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(422, first.getResponse().getStatus());

        MvcResult put = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/vendors/" + vendor.getCode() + "/mapping")
                        .header("Authorization", bearer(analyst()))
                        .contentType("application/json")
                        .content("[{\"sourceHeader\":\"SKU\",\"targetField\":\"SKU\"},"
                                + "{\"sourceHeader\":\"Cost Price\",\"targetField\":\"COST_PRICE\"}]"))
                .andReturn();
        assertEquals(200, put.getResponse().getStatus());
        JsonNode saved = json(put);
        assertEquals(2, saved.size());
        assertEquals("SKU", saved.get(0).path("sourceHeader").asText());
        assertEquals("COST_PRICE", saved.get(1).path("targetField").asText());

        MvcResult read = getJson("/api/vendors/" + vendor.getCode() + "/mapping", viewer());
        assertEquals(2, json(read).size());

        MvcResult second = upload(analyst(), vendor.getCode(), "v.csv",
                "SKU,Description,Cost Price\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(201, second.getResponse().getStatus());
        assertEquals("PARSED", json(second).path("status").asText());
    }

    @Test
    void fileListAndDetailShapesMatchTheContract() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        Product product = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");
        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {product.getSku(), "Only", "5,200.00"},
        });

        MvcResult list = getJson("/api/files?page=0&size=10", viewer());
        JsonNode page = json(list);
        assertTrue(page.has("content") && page.has("totalElements") && page.has("totalPages")
                && page.has("number") && page.has("size"));
        JsonNode mine = null;
        for (JsonNode file : page.get("content")) {
            if (file.get("id").asLong() == fileId) {
                mine = file;
            }
        }
        assertNotNull(mine);
        for (String field : new String[] {"id", "vendorCode", "filename", "status", "uploadedAt",
                "uploadedByName", "totalRows", "updatedRows", "errorRows"}) {
            assertTrue(mine.has(field), "fileRes missing " + field);
        }

        MvcResult detail = getJson("/api/files/" + fileId, viewer());
        JsonNode body = json(detail);
        for (String key : new String[] {"OK", "BREACH", "NEW_SKU", "NOT_FOUND", "DUPLICATE"}) {
            assertTrue(body.path("flagCounts").has(key), "flagCounts missing " + key);
        }
        for (String key : new String[] {"PENDING", "APPLIED", "SKIPPED"}) {
            assertTrue(body.path("statusCounts").has(key), "statusCounts missing " + key);
        }
        assertEquals(1, body.path("flagCounts").path("OK").asLong());
        assertEquals(1, body.path("statusCounts").path("PENDING").asLong());
    }
}
