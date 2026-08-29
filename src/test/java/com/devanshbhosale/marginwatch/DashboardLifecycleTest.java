package com.devanshbhosale.marginwatch;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.devanshbhosale.marginwatch.domain.PriceFile;
import com.devanshbhosale.marginwatch.domain.PriceFileStatus;
import com.devanshbhosale.marginwatch.domain.Product;
import com.devanshbhosale.marginwatch.domain.Vendor;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class DashboardLifecycleTest extends IntegrationTestBase {

    @Test
    void sweeperMarksInterruptedFilesFailed() {
        Vendor vendor = newVendor();
        PriceFile uploaded = priceFile(vendor, PriceFileStatus.UPLOADED);
        PriceFile parsing = priceFile(vendor, PriceFileStatus.PARSING);

        fileService.sweepInterrupted();

        PriceFile failed1 = fileRepository.findById(uploaded.getId()).orElseThrow();
        PriceFile failed2 = fileRepository.findById(parsing.getId()).orElseThrow();
        assertEquals(PriceFileStatus.FAILED, failed1.getStatus());
        assertEquals("Processing interrupted by restart", failed1.getErrorReport());
        assertNotNull(failed1.getProcessedAt());
        assertEquals(PriceFileStatus.FAILED, failed2.getStatus());
    }

    @Test
    void exportStreamsReadableXlsx() throws Exception {
        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        Product product = newProduct(vendor, unique("Cat"), "5000.00", "6000.00");
        long fileId = uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {product.getSku(), "Export me", "5,200.00"},
        });

        MvcResult result = mockMvc.perform(get("/api/files/" + fileId + "/export")
                        .header("Authorization", bearer(viewer())))
                .andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                result.getResponse().getContentType());
        assertTrue(result.getResponse().getHeader("Content-Disposition")
                .contains("marginwatch-file-" + fileId + "-report.xlsx"));

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Row #", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Flag", sheet.getRow(0).getCell(7).getStringCellValue());
            Row data = sheet.getRow(1);
            assertEquals(product.getSku(), data.getCell(1).getStringCellValue());
            assertEquals("OK", data.getCell(7).getStringCellValue());
            assertEquals(2, sheet.getLastRowNum() + 1);
        }
    }

    @Test
    void dashboardShowsSeedContentAndShapes() throws Exception {
        MvcResult result = getJson("/api/dashboard", viewer());
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = json(result);

        JsonNode stats = body.path("stats");
        assertTrue(stats.path("skusMonitored").asLong() >= 63, "seeded catalog must be counted");
        assertTrue(stats.path("pendingRows").asLong() >= 11, "seeded pending file rows must be counted");
        assertTrue(stats.path("pendingBreaches").asLong() >= 4, "seeded breaches must be counted");
        assertTrue(stats.path("filesLast30d").asLong() >= 3, "seeded files must be counted");
        assertTrue(stats.path("marginAtRisk").asDouble() < 0, "pending breaches burn margin");

        JsonNode changesPerDay = body.path("changesPerDay");
        assertEquals(14, changesPerDay.size());
        long appliedSum = 0;
        for (JsonNode point : changesPerDay) {
            appliedSum += point.path("applied").asLong();
        }
        assertTrue(appliedSum >= 18, "seeded applied rows must appear in the 14-day series");

        assertTrue(body.path("marginByCategory").size() >= 6);
        assertTrue(body.path("marginByCategory").toString().contains("Networking"));

        boolean ciscoNetworking = false;
        for (JsonNode cell : body.path("heatmap")) {
            if ("CISCO".equals(cell.path("vendorCode").asText())
                    && "Networking".equals(cell.path("category").asText())
                    && cell.path("breaches").asLong() >= 4) {
                ciscoNetworking = true;
            }
        }
        assertTrue(ciscoNetworking, "seeded CISCO breach rows must show in the heatmap");

        JsonNode topMovers = body.path("topMovers");
        assertTrue(topMovers.size() <= 8);
        assertTrue(topMovers.size() > 0);
        JsonNode mover = topMovers.get(0);
        assertTrue(mover.has("sku") && mover.has("vendorCode") && mover.has("oldCost")
                && mover.has("newCost") && mover.has("changePct") && mover.has("flag")
                && mover.has("status") && mover.has("fileId") && mover.has("fileUploadedAt"));
    }

    @Test
    void dashboardMarginAtRiskAddsNewPendingBreach() throws Exception {
        MvcResult before = getJson("/api/dashboard", viewer());
        BigDecimal riskBefore = new BigDecimal(json(before).path("stats").path("marginAtRisk").asText());

        Vendor vendor = newVendor();
        mapVendor(vendor, "SKU", "SKU", "Description", "DESCRIPTION", "Cost Price", "COST_PRICE");
        Product product = newProduct(vendor, unique("Cat"), "1000.00", "2000.00");
        // 1850 on selling 2000 => 7.5% margin, below the global floor of 8 => breach
        uploadCsv(analyst(), vendor.getCode(), new String[][] {
                {"SKU", "Description", "Cost Price"},
                {product.getSku(), "Breach", "1,850.00"},
        });

        MvcResult after = getJson("/api/dashboard", viewer());
        BigDecimal riskAfter = new BigDecimal(json(after).path("stats").path("marginAtRisk").asText());
        assertEquals(0, riskAfter.subtract(riskBefore).compareTo(new BigDecimal("-850.00")));
        long breachesBefore = json(before).path("stats").path("pendingBreaches").asLong();
        assertEquals(breachesBefore + 1, json(after).path("stats").path("pendingBreaches").asLong());
    }

    private PriceFile priceFile(Vendor vendor, PriceFileStatus status) {
        PriceFile file = new PriceFile();
        file.setVendor(vendor);
        file.setFilename("fake-" + unique("f") + ".csv");
        file.setStatus(status);
        file.setUploadedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        file.setUploadedBy(analyst());
        return fileRepository.save(file);
    }
}
