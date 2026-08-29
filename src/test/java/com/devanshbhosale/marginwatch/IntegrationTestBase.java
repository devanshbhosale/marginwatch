package com.devanshbhosale.marginwatch;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.devanshbhosale.marginwatch.domain.FileColumnMapping;
import com.devanshbhosale.marginwatch.domain.FileColumnMapping.TargetField;
import com.devanshbhosale.marginwatch.domain.Product;
import com.devanshbhosale.marginwatch.domain.User;
import com.devanshbhosale.marginwatch.domain.UserRole;
import com.devanshbhosale.marginwatch.domain.Vendor;
import com.devanshbhosale.marginwatch.repository.AuditLogRepository;
import com.devanshbhosale.marginwatch.repository.FileColumnMappingRepository;
import com.devanshbhosale.marginwatch.repository.MarginPolicyRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRowRepository;
import com.devanshbhosale.marginwatch.repository.ProductRepository;
import com.devanshbhosale.marginwatch.repository.UserRepository;
import com.devanshbhosale.marginwatch.repository.VendorRepository;
import com.devanshbhosale.marginwatch.security.JwtService;
import com.devanshbhosale.marginwatch.service.FileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared fixtures for the API tests. Every test builds its own vendors /
 * products / files with unique codes and SKUs so nothing depends on seed data
 * or on execution order. Seeded accounts are used for the role matrix.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JwtService jwtService;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected UserRepository userRepository;
    @Autowired protected VendorRepository vendorRepository;
    @Autowired protected ProductRepository productRepository;
    @Autowired protected FileColumnMappingRepository mappingRepository;
    @Autowired protected MarginPolicyRepository policyRepository;
    @Autowired protected PriceFileRepository fileRepository;
    @Autowired protected PriceFileRowRepository rowRepository;
    @Autowired protected AuditLogRepository auditRepository;
    @Autowired protected FileService fileService;

    // ---- identities

    protected User admin() {
        return userRepository.findByEmail("priya.nair@marginwatch.in").orElseThrow();
    }

    protected User analyst() {
        return userRepository.findByEmail("rahul.mehta@marginwatch.in").orElseThrow();
    }

    protected User viewer() {
        return userRepository.findByEmail("sneha.rao@marginwatch.in").orElseThrow();
    }

    protected String bearer(User user) {
        return "Bearer " + jwtService.issue(user);
    }

    // ---- fixture builders

    protected String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    protected Vendor newVendor() {
        Vendor vendor = new Vendor();
        vendor.setCode(unique("V"));
        vendor.setName("Test Vendor " + unique("N"));
        return vendorRepository.save(vendor);
    }

    /** pairs: header, TARGET_FIELD, header, TARGET_FIELD... */
    protected void mapVendor(Vendor vendor, String... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            FileColumnMapping mapping = new FileColumnMapping();
            mapping.setVendor(vendor);
            mapping.setSourceHeader(pairs[i]);
            mapping.setTargetField(TargetField.valueOf(pairs[i + 1]));
            mappingRepository.save(mapping);
        }
    }

    protected Product newProduct(Vendor vendor, String category, String cost, String selling) {
        Product product = new Product();
        product.setSku(unique("SKU"));
        product.setVendor(vendor);
        product.setCategory(category);
        product.setName("Test Product " + product.getSku());
        product.setCurrentCost(new BigDecimal(cost));
        product.setSellingPrice(new BigDecimal(selling));
        return productRepository.save(product);
    }

    // ---- request helpers

    protected MvcResult upload(User actor, String vendorCode, String filename, byte[] content) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/files")
                        .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
                        .param("vendorCode", vendorCode)
                        .header("Authorization", bearer(actor)))
                .andReturn();
    }

    protected MvcResult postJson(String url, User actor, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .header("Authorization", bearer(actor))
                        .contentType("application/json")
                        .content(body == null ? "" : body))
                .andReturn();
    }

    protected MvcResult getJson(String url, User actor) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url);
        if (actor != null) {
            request.header("Authorization", bearer(actor));
        }
        return mockMvc.perform(request).andReturn();
    }

    protected JsonNode json(MvcResult result) throws java.io.IOException {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ---- price-file builders

    /** rows[0] is the header row. Fields containing commas are quoted per RFC 4180. */
    protected String csv(String[][] rows) {
        try (var printer = new org.apache.commons.csv.CSVPrinter(new StringBuilder(),
                org.apache.commons.csv.CSVFormat.DEFAULT)) {
            for (String[] row : rows) {
                printer.printRecord((Object[]) row);
            }
            printer.flush();
            return printer.getOut().toString();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    protected byte[] xlsx(String[] header, String[][] rows) throws java.io.IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Price");
            var headerRow = sheet.createRow(0);
            for (int c = 0; c < header.length; c++) {
                headerRow.createCell(c).setCellValue(header[c]);
            }
            int r = 1;
            for (String[] row : rows) {
                var dataRow = sheet.createRow(r++);
                for (int c = 0; c < row.length; c++) {
                    dataRow.createCell(c).setCellValue(row[c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Uploads a CSV for a mapped vendor and returns the parsed file id. */
    protected long uploadCsv(User actor, String vendorCode, String[][] rows) throws Exception {
        MvcResult result = upload(actor, vendorCode, unique("file") + ".csv",
                csv(rows).getBytes(StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertEquals(201, result.getResponse().getStatus(),
                "upload should parse inline in tests: " + result.getResponse().getContentAsString());
        return json(result).get("id").asLong();
    }

    protected JsonNode rowsOf(long fileId) throws Exception {
        return json(getJson("/api/files/" + fileId + "/rows?size=100", admin()));
    }

    protected JsonNode rowBySku(JsonNode rows, String sku) {
        for (JsonNode row : rows.get("content")) {
            if (sku.equals(row.path("sku").asText())) {
                return row;
            }
        }
        return null;
    }

    protected List<Long> fileRowIds(long fileId) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (JsonNode row : rowsOf(fileId).get("content")) {
            ids.add(row.get("id").asLong());
        }
        return ids;
    }
}
