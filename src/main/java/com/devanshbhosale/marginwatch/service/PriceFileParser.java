package com.devanshbhosale.marginwatch.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.devanshbhosale.marginwatch.domain.FileColumnMapping.TargetField;
import com.devanshbhosale.marginwatch.domain.PriceFile;
import com.devanshbhosale.marginwatch.domain.PriceFileRow;
import com.devanshbhosale.marginwatch.domain.Product;
import com.devanshbhosale.marginwatch.domain.RowFlag;
import com.devanshbhosale.marginwatch.domain.RowStatus;
import com.devanshbhosale.marginwatch.repository.FileColumnMappingRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRowRepository;
import com.devanshbhosale.marginwatch.repository.ProductRepository;

/**
 * Turns uploaded bytes into PriceFileRows: CSV via commons-csv, XLSX via POI
 * (DOM read is fine for the 5000-row cap). Applies the resolution pipeline
 * (NOT_FOUND → DUPLICATE → NEW_SKU → BREACH/OK), the >5000-row cap and the
 * >50% NOT_FOUND staleness guard, all in one transaction.
 */
@Component
public class PriceFileParser {

    private final FileColumnMappingRepository mappingRepository;
    private final PriceFileRepository fileRepository;
    private final PriceFileRowRepository rowRepository;
    private final ProductRepository productRepository;
    private final DiffMath diffMath;
    private final int maxRows;

    public PriceFileParser(FileColumnMappingRepository mappingRepository,
                           PriceFileRepository fileRepository,
                           PriceFileRowRepository rowRepository,
                           ProductRepository productRepository,
                           DiffMath diffMath,
                           @Value("${app.files.max-rows}") int maxRows) {
        this.mappingRepository = mappingRepository;
        this.fileRepository = fileRepository;
        this.rowRepository = rowRepository;
        this.productRepository = productRepository;
        this.diffMath = diffMath;
        this.maxRows = maxRows;
    }

    @Transactional
    public void process(Long fileId, byte[] content) {
        PriceFile file = fileRepository.findById(fileId).orElse(null);
        if (file == null) {
            return;
        }
        try {
            file.setStatus(com.devanshbhosale.marginwatch.domain.PriceFileStatus.PARSING);
            ParsedSheet sheet = readSheet(file.getFilename(), content);
            if (sheet.rows().size() > maxRows) {
                FileService.fail(file, "File has " + sheet.rows().size() + " data rows — the limit is " + maxRows);
                return;
            }
            List<PriceFileRow> rows = resolve(file, sheet);
            long notFound = rows.stream().filter(r -> r.getFlag() == RowFlag.NOT_FOUND).count();
            if (!rows.isEmpty() && notFound * 2 > rows.size()) {
                FileService.fail(file, "Over 50% of rows could not be resolved — column mapping is likely stale;"
                        + " re-map and re-upload");
                return;
            }
            rowRepository.saveAll(rows);
            long notFoundCount = rows.stream().filter(r -> r.getFlag() == RowFlag.NOT_FOUND).count();
            long updated = rows.stream()
                    .filter(r -> r.getFlag() == RowFlag.OK || r.getFlag() == RowFlag.BREACH)
                    .count();
            file.setStatus(com.devanshbhosale.marginwatch.domain.PriceFileStatus.PARSED);
            file.setTotalRows(rows.size());
            file.setUpdatedRows((int) updated);
            file.setErrorRows((int) notFoundCount);
            file.setProcessedAt(LocalDateTime.now(FileService.IST));
        } catch (Exception e) {
            FileService.fail(file, "Parsing failed: " + e.getMessage());
        }
    }

    private List<PriceFileRow> resolve(PriceFile file, ParsedSheet sheet) {
        Map<TargetField, Integer> columns = resolveColumns(file, sheet.headers());
        Integer skuIdx = columns.get(TargetField.SKU);
        Integer descIdx = columns.get(TargetField.DESCRIPTION);
        Integer costIdx = columns.get(TargetField.COST_PRICE);

        List<PriceFileRow> rows = new ArrayList<>();
        Set<String> seenSkus = new HashSet<>();
        int rowNumber = 0;
        for (String[] cells : sheet.rows()) {
            rowNumber++;
            PriceFileRow row = baseRow(file, rowNumber, cells);
            String sku = cell(cells, skuIdx);
            row.setSku(blankToNull(sku));
            row.setProductName(blankToNull(cell(cells, descIdx)));
            row.setNewCost(parseCost(cell(cells, costIdx)));

            if (row.getSku() == null) {
                notFound(row, "SKU missing");
            } else if (row.getNewCost() == null) {
                notFound(row, "Cost not numeric");
            } else if (!seenSkus.add(row.getSku().toUpperCase(Locale.ROOT))) {
                row.setFlag(RowFlag.DUPLICATE);
                row.setNote("SKU already seen earlier in this file");
            } else {
                resolveAgainstCatalog(file, row);
            }
            rows.add(row);
        }
        return rows;
    }

    private void resolveAgainstCatalog(PriceFile file, PriceFileRow row) {
        Optional<Product> found = productRepository.findBySkuIgnoreCase(row.getSku());
        if (found.isEmpty()) {
            row.setFlag(RowFlag.NEW_SKU);
            return;
        }
        Product product = found.get();
        if (!product.getVendor().getId().equals(file.getVendor().getId())) {
            notFound(row, "SKU belongs to another vendor");
            return;
        }
        row.setOldCost(product.getCurrentCost());
        row.setChangePct(DiffMath.changePct(product.getCurrentCost(), row.getNewCost()));
        row.setResultingMarginPct(DiffMath.marginPct(product.getSellingPrice(), row.getNewCost()));

        boolean loss = product.getSellingPrice() != null
                && row.getNewCost().compareTo(product.getSellingPrice()) >= 0;
        BigDecimal floor = diffMath.floorFor(product.getCategory());
        boolean belowFloor = !loss && floor != null && row.getResultingMarginPct() != null
                && row.getResultingMarginPct().compareTo(floor) < 0;
        row.setFlag(loss || belowFloor ? RowFlag.BREACH : RowFlag.OK);
    }

    private static void notFound(PriceFileRow row, String note) {
        row.setFlag(RowFlag.NOT_FOUND);
        row.setNote(note);
    }

    private PriceFileRow baseRow(PriceFile file, int rowNumber, String[] cells) {
        PriceFileRow row = new PriceFileRow();
        row.setPriceFile(file);
        row.setRowNumber(rowNumber);
        row.setRawRow(String.join(", ", cells));
        row.setStatus(RowStatus.PENDING);
        row.setFlag(RowFlag.NOT_FOUND);
        return row;
    }

    private Map<TargetField, Integer> resolveColumns(PriceFile file, List<String> headers) {
        Map<String, Integer> byHeader = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header != null && !header.isBlank()) {
                byHeader.putIfAbsent(header.trim().toLowerCase(Locale.ROOT), i);
            }
        }
        Map<TargetField, Integer> columns = new LinkedHashMap<>();
        for (var mapping : mappingRepository.findByVendorId(file.getVendor().getId())) {
            Integer index = byHeader.get(mapping.getSourceHeader().trim().toLowerCase(Locale.ROOT));
            if (index != null) {
                columns.putIfAbsent(mapping.getTargetField(), index);
            }
        }
        return columns;
    }

    private ParsedSheet readSheet(String filename, byte[] content) throws IOException {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return readCsv(content);
        }
        if (name.endsWith(".xlsx")) {
            return readXlsx(content);
        }
        throw new IOException("Unsupported file type — upload .csv or .xlsx");
    }

    private ParsedSheet readCsv(byte[] content) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).build();
        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8), format)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                return new ParsedSheet(List.of(), List.of());
            }
            List<String> headers = new ArrayList<>();
            records.get(0).forEach(headers::add);
            List<String[]> rows = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                List<String> cells = new ArrayList<>();
                records.get(i).forEach(cells::add);
                if (cells.stream().anyMatch(c -> c != null && !c.isBlank())) {
                    rows.add(cells.toArray(String[]::new));
                }
            }
            return new ParsedSheet(headers, rows);
        }
    }

    private ParsedSheet readXlsx(byte[] content) throws IOException {
        DataFormatter formatter = new DataFormatter();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> headers = new ArrayList<>();
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    org.apache.poi.ss.usermodel.Cell cell = headerRow.getCell(c);
                    headers.add(cell == null ? "" : formatter.formatCellValue(cell));
                }
            }
            List<String[]> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                // index-based so blank cells keep the column alignment
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.getCell(c);
                    cells.add(cell == null ? "" : formatter.formatCellValue(cell));
                }
                if (cells.stream().anyMatch(v -> v != null && !v.isBlank())) {
                    rows.add(cells.toArray(String[]::new));
                }
            }
            return new ParsedSheet(headers, rows);
        }
    }

    /** Strips ₹, Rs., INR, commas and spaces: "₹ 12,500.00" → 12500.00. */
    static BigDecimal parseCost(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace('₹', ' ')
                .replaceAll("(?i)rs\\.?|inr", " ")
                .replace(",", "")
                .replace(" ", "")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cell(String[] cells, Integer index) {
        if (index == null || index >= cells.length) {
            return null;
        }
        return cells[index];
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ParsedSheet(List<String> headers, List<String[]> rows) {
    }
}
