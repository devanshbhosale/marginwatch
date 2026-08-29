package com.devanshbhosale.marginwatch.web;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devanshbhosale.marginwatch.domain.PriceFile;
import com.devanshbhosale.marginwatch.domain.PriceFileRow;
import com.devanshbhosale.marginwatch.domain.RowFlag;
import com.devanshbhosale.marginwatch.domain.RowStatus;
import com.devanshbhosale.marginwatch.domain.User;
import com.devanshbhosale.marginwatch.dto.ApplyAllReq;
import com.devanshbhosale.marginwatch.dto.ApplyAllRes;
import com.devanshbhosale.marginwatch.dto.FileDetailRes;
import com.devanshbhosale.marginwatch.dto.FileRes;
import com.devanshbhosale.marginwatch.dto.PageRes;
import com.devanshbhosale.marginwatch.dto.RowRes;
import com.devanshbhosale.marginwatch.exception.ApiException;
import com.devanshbhosale.marginwatch.repository.PriceFileRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRowRepository;
import com.devanshbhosale.marginwatch.service.FileService;
import com.devanshbhosale.marginwatch.service.RowService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
public class FileController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final FileService fileService;
    private final RowService rowService;
    private final PriceFileRepository fileRepository;
    private final PriceFileRowRepository rowRepository;

    public FileController(FileService fileService, RowService rowService,
                          PriceFileRepository fileRepository, PriceFileRowRepository rowRepository) {
        this.fileService = fileService;
        this.rowService = rowService;
        this.fileRepository = fileRepository;
        this.rowRepository = rowRepository;
    }

    @PostMapping(value = "/api/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileRes> upload(@RequestParam("vendorCode") String vendorCode,
                                          @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                          @AuthenticationPrincipal User actor) throws IOException {
        FileRes res = fileService.upload(vendorCode, file.getOriginalFilename(), file.getBytes(), actor);
        return ResponseEntity.status(fileService.isAsync() ? HttpStatus.ACCEPTED : HttpStatus.CREATED).body(res);
    }

    @GetMapping("/api/files")
    public PageRes<FileRes> list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        var pageResult = fileRepository.findAllByOrderByUploadedAtDescIdDesc(
                PageRequest.of(Math.max(page, 0), pageSize));
        return PageRes.of(pageResult, pageResult.map(fileService::toRes).getContent());
    }

    @GetMapping("/api/files/{id}")
    public FileDetailRes detail(@PathVariable Long id) {
        PriceFile file = fileRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "File not found"));
        return fileService.toDetailRes(file);
    }

    @GetMapping("/api/files/{id}/rows")
    public PageRes<RowRes> rows(@PathVariable Long id,
                                @RequestParam(required = false) String flag,
                                @RequestParam(required = false) String status,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        requireFile(id);
        int pageSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), pageSize, Sort.by("rowNumber").ascending());
        RowFlag rowFlag = parseEnum(RowFlag.class, flag, "flag");
        RowStatus rowStatus = parseEnum(RowStatus.class, status, "status");
        var result = (rowFlag != null && rowStatus != null)
                ? rowRepository.findByPriceFileIdAndFlagAndStatus(id, rowFlag, rowStatus, pageRequest)
                : rowFlag != null ? rowRepository.findByPriceFileIdAndFlag(id, rowFlag, pageRequest)
                : rowStatus != null ? rowRepository.findByPriceFileIdAndStatus(id, rowStatus, pageRequest)
                : rowRepository.findByPriceFileId(id, pageRequest);
        return rowService.toPageRes(result);
    }

    @PostMapping("/api/files/{id}/apply-all")
    public ApplyAllRes applyAll(@PathVariable Long id,
                                @RequestBody(required = false) ApplyAllReq request,
                                @AuthenticationPrincipal User actor) {
        boolean includeBreaches = request != null && Boolean.TRUE.equals(request.includeBreaches());
        String reason = request == null ? null : request.overrideReason();
        return rowService.applyAll(id, includeBreaches, reason, actor);
    }

    /** Streaming SXSSF export; one row per price-file row. */
    @GetMapping("/api/files/{id}/export")
    public void export(@PathVariable Long id, HttpServletResponse response) throws IOException {
        PriceFile file = requireFile(id);
        List<PriceFileRow> rows = rowRepository.findByPriceFileIdOrderByRowNumberAsc(id);
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename=marginwatch-file-" + id + "-report.xlsx");
        try (var workbook = new org.apache.poi.xssf.streaming.SXSSFWorkbook(100)) {
            var sheet = workbook.createSheet("Report");
            var header = sheet.createRow(0);
            String[] columns = {"Row #", "SKU", "Product", "Old Cost", "New Cost", "Change %",
                    "Resulting Margin %", "Flag", "Status", "Note"};
            for (int c = 0; c < columns.length; c++) {
                header.createCell(c).setCellValue(columns[c]);
            }
            int r = 1;
            for (PriceFileRow row : rows) {
                var sheetRow = sheet.createRow(r++);
                sheetRow.createCell(0).setCellValue(row.getRowNumber());
                sheetRow.createCell(1).setCellValue(nullSafe(row.getSku()));
                sheetRow.createCell(2).setCellValue(nullSafe(row.getProductName()));
                writeNumber(sheetRow.createCell(3), row.getOldCost());
                writeNumber(sheetRow.createCell(4), row.getNewCost());
                writeNumber(sheetRow.createCell(5), row.getChangePct());
                writeNumber(sheetRow.createCell(6), row.getResultingMarginPct());
                sheetRow.createCell(7).setCellValue(row.getFlag().name());
                sheetRow.createCell(8).setCellValue(row.getStatus().name());
                sheetRow.createCell(9).setCellValue(nullSafe(row.getNote()));
            }
            workbook.write(response.getOutputStream());
            workbook.dispose();
        }
    }

    private PriceFile requireFile(Long id) {
        return fileRepository.findById(id).orElseThrow(() -> new ApiException(404, "File not found"));
    }

    private static void writeNumber(org.apache.poi.ss.usermodel.Cell cell, java.math.BigDecimal value) {
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Unknown " + name + ": " + raw);
        }
    }
}
