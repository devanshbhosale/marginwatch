package com.devanshbhosale.marginwatch.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devanshbhosale.marginwatch.domain.AuditLog;
import com.devanshbhosale.marginwatch.domain.FileColumnMapping;
import com.devanshbhosale.marginwatch.domain.FileColumnMapping.TargetField;
import com.devanshbhosale.marginwatch.domain.PriceFile;
import com.devanshbhosale.marginwatch.domain.PriceFileStatus;
import com.devanshbhosale.marginwatch.domain.User;
import com.devanshbhosale.marginwatch.domain.Vendor;
import com.devanshbhosale.marginwatch.dto.FileDetailRes;
import com.devanshbhosale.marginwatch.dto.FileRes;
import com.devanshbhosale.marginwatch.exception.ApiException;
import com.devanshbhosale.marginwatch.repository.AuditLogRepository;
import com.devanshbhosale.marginwatch.repository.FileColumnMappingRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRowRepository;
import com.devanshbhosale.marginwatch.repository.VendorRepository;

/**
 * Upload intake: validates the vendor mapping, creates the PriceFile and hands
 * the bytes to the parser (inline when app.files.async=false, otherwise on the
 * single-thread parse executor). Also owns the startup sweeper.
 */
@Service
public class FileService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final VendorRepository vendorRepository;
    private final FileColumnMappingRepository mappingRepository;
    private final PriceFileRepository fileRepository;
    private final PriceFileRowRepository rowRepository;
    private final AuditLogRepository auditRepository;
    private final PriceFileParser parser;
    private final ExecutorService parseExecutor;
    private final boolean async;

    public FileService(VendorRepository vendorRepository,
                       FileColumnMappingRepository mappingRepository,
                       PriceFileRepository fileRepository,
                       PriceFileRowRepository rowRepository,
                       AuditLogRepository auditRepository,
                       PriceFileParser parser,
                       ExecutorService parseExecutor,
                       @Value("${app.files.async}") boolean async) {
        this.vendorRepository = vendorRepository;
        this.mappingRepository = mappingRepository;
        this.fileRepository = fileRepository;
        this.rowRepository = rowRepository;
        this.auditRepository = auditRepository;
        this.parser = parser;
        this.parseExecutor = parseExecutor;
        this.async = async;
    }

    /** Dedicated single-thread executor bean is defined on MarginWatchApplication. */

    public boolean isAsync() {
        return async;
    }

    /**
     * Mapping gate runs before the file is created: a vendor without both SKU
     * and COST_PRICE mapped gets a 422 and no PriceFile row at all.
     */
    public FileRes upload(String vendorCode, String filename, byte[] content, User uploader) {
        Vendor vendor = vendorRepository.findByCode(vendorCode == null ? "" : vendorCode.trim())
                .orElseThrow(() -> new ApiException(404, "Vendor not found"));
        requireMappable(vendor);

        PriceFile file = new PriceFile();
        file.setVendor(vendor);
        file.setFilename(filename == null ? "upload" : filename);
        file.setStatus(PriceFileStatus.UPLOADED);
        file.setUploadedAt(LocalDateTime.now(IST));
        file.setUploadedBy(uploader);
        file = fileRepository.save(file);
        auditRepository.save(audit(uploader, file));

        Long fileId = file.getId();
        if (async) {
            parseExecutor.submit(() -> parser.process(fileId, content));
        } else {
            parser.process(fileId, content);
        }
        return toRes(fileRepository.findById(fileId).orElseThrow());
    }

    /**
     * Startup sweeper: a Render restart must not leave files stuck mid-flight.
     * Anything still UPLOADED or PARSING at boot is failed with a fixed reason.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void sweepInterrupted() {
        List<PriceFile> stale = fileRepository.findByStatusIn(
                List.of(PriceFileStatus.UPLOADED, PriceFileStatus.PARSING));
        for (PriceFile file : stale) {
            fail(file, "Processing interrupted by restart");
        }
    }

    static void fail(PriceFile file, String message) {
        file.setStatus(PriceFileStatus.FAILED);
        file.setErrorReport(message);
        file.setProcessedAt(LocalDateTime.now(IST));
    }

    void requireMappable(Vendor vendor) {
        List<TargetField> targets = mappingRepository.findByVendorId(vendor.getId()).stream()
                .map(FileColumnMapping::getTargetField)
                .toList();
        if (!targets.contains(TargetField.SKU) || !targets.contains(TargetField.COST_PRICE)) {
            throw new ApiException(422, "No column mapping saved for " + vendor.getCode()
                    + " — map the vendor's columns first");
        }
    }

    private AuditLog audit(User uploader, PriceFile file) {
        AuditLog entry = new AuditLog();
        entry.setActorEmail(uploader.getEmail());
        entry.setAction("FILE_UPLOADED");
        entry.setEntityRef("PRICE_FILE:" + file.getId());
        entry.setDetails(file.getFilename() + " (" + file.getVendor().getCode() + ")");
        entry.setCreatedAt(file.getUploadedAt());
        return entry;
    }

    public FileRes toRes(PriceFile file) {
        return new FileRes(file.getId(), file.getVendor().getCode(), file.getFilename(),
                file.getStatus().name(), file.getUploadedAt(),
                file.getUploadedBy() == null ? null : file.getUploadedBy().getFullName(),
                file.getTotalRows(), file.getUpdatedRows(), file.getErrorRows(), file.getErrorReport(),
                file.getProcessedAt());
    }

    public FileDetailRes toDetailRes(PriceFile file) {
        java.util.Map<String, Long> flagCounts = new java.util.LinkedHashMap<>();
        for (com.devanshbhosale.marginwatch.domain.RowFlag flag : com.devanshbhosale.marginwatch.domain.RowFlag.values()) {
            flagCounts.put(flag.name(), 0L);
        }
        for (Object[] cell : rowRepository.countByFlagGrouped(file.getId())) {
            flagCounts.put(((com.devanshbhosale.marginwatch.domain.RowFlag) cell[0]).name(), (Long) cell[1]);
        }
        java.util.Map<String, Long> statusCounts = new java.util.LinkedHashMap<>();
        for (com.devanshbhosale.marginwatch.domain.RowStatus status : com.devanshbhosale.marginwatch.domain.RowStatus.values()) {
            statusCounts.put(status.name(), 0L);
        }
        for (Object[] cell : rowRepository.countByStatusGrouped(file.getId())) {
            statusCounts.put(((com.devanshbhosale.marginwatch.domain.RowStatus) cell[0]).name(), (Long) cell[1]);
        }
        FileRes base = toRes(file);
        return new FileDetailRes(base.id(), base.vendorCode(), base.filename(), base.status(), base.uploadedAt(),
                base.uploadedByName(), base.totalRows(), base.updatedRows(), base.errorRows(), base.errorReport(),
                base.processedAt(), flagCounts, statusCounts);
    }
}
