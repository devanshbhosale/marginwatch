package com.devanshbhosale.marginwatch.web;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.devanshbhosale.marginwatch.domain.AuditLog;
import com.devanshbhosale.marginwatch.domain.FileColumnMapping;
import com.devanshbhosale.marginwatch.domain.FileColumnMapping.TargetField;
import com.devanshbhosale.marginwatch.domain.User;
import com.devanshbhosale.marginwatch.domain.Vendor;
import com.devanshbhosale.marginwatch.dto.MappingRes;
import com.devanshbhosale.marginwatch.dto.VendorRes;
import com.devanshbhosale.marginwatch.exception.ApiException;
import com.devanshbhosale.marginwatch.repository.AuditLogRepository;
import com.devanshbhosale.marginwatch.repository.FileColumnMappingRepository;
import com.devanshbhosale.marginwatch.repository.VendorRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;

@RestController
public class VendorController {

    private final VendorRepository vendorRepository;
    private final FileColumnMappingRepository mappingRepository;
    private final AuditLogRepository auditRepository;

    public VendorController(VendorRepository vendorRepository,
                            FileColumnMappingRepository mappingRepository,
                            AuditLogRepository auditRepository) {
        this.vendorRepository = vendorRepository;
        this.mappingRepository = mappingRepository;
        this.auditRepository = auditRepository;
    }

    @GetMapping("/api/vendors")
    public List<VendorRes> vendors() {
        return vendorRepository.findAll().stream()
                .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
                .map(vendor -> {
                    List<FileColumnMapping> mappings = mappingRepository.findByVendorId(vendor.getId());
                    boolean mapped = mappings.stream().anyMatch(m -> m.getTargetField() == TargetField.SKU)
                            && mappings.stream().anyMatch(m -> m.getTargetField() == TargetField.COST_PRICE);
                    return new VendorRes(vendor.getCode(), vendor.getName(), mapped, mappings.size());
                })
                .toList();
    }

    @GetMapping("/api/vendors/{code}/mapping")
    public List<MappingRes> mapping(@PathVariable String code) {
        Vendor vendor = vendorRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(404, "Vendor not found"));
        return mappingRepository.findByVendorIdOrderByIdAsc(vendor.getId()).stream()
                .map(mapping -> new MappingRes(mapping.getSourceHeader(), mapping.getTargetField()))
                .toList();
    }

    /** Replaces all mappings for the vendor; headers are stored trimmed. */
    @PutMapping("/api/vendors/{code}/mapping")
    @Transactional
    public List<MappingRes> replaceMapping(@PathVariable String code,
                                           @RequestBody List<MappingRes> mappings,
                                           @AuthenticationPrincipal User actor) {
        Vendor vendor = vendorRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(404, "Vendor not found"));
        List<MappingRes> body = mappings == null ? List.of() : mappings;

        Set<String> seen = new HashSet<>();
        for (MappingRes mapping : body) {
            if (mapping.sourceHeader() == null || mapping.sourceHeader().isBlank()) {
                throw new ApiException(422, "sourceHeader is required for every mapping");
            }
            if (mapping.targetField() == null) {
                throw new ApiException(422, "targetField is required for every mapping");
            }
            String key = mapping.sourceHeader().trim().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new ApiException(409, "Duplicate source header: " + mapping.sourceHeader().trim());
            }
        }

        // Flush the removals before re-inserting so the (vendor, header) unique
        // constraint sees the old rows gone.
        mappingRepository.deleteAll(mappingRepository.findByVendorId(vendor.getId()));
        mappingRepository.flush();

        List<FileColumnMapping> saved = body.stream()
                .map(mapping -> {
                    FileColumnMapping entity = new FileColumnMapping();
                    entity.setVendor(vendor);
                    entity.setSourceHeader(mapping.sourceHeader().trim());
                    entity.setTargetField(mapping.targetField());
                    return mappingRepository.save(entity);
                })
                .toList();

        AuditLog entry = new AuditLog();
        entry.setActorEmail(actor.getEmail());
        entry.setAction("MAPPING_UPDATED");
        entry.setEntityRef("VENDOR:" + vendor.getCode());
        entry.setDetails(saved.size() + " column mappings saved: "
                + saved.stream().map(FileColumnMapping::getSourceHeader).reduce((a, b) -> a + ", " + b).orElse("none"));
        entry.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        auditRepository.save(entry);

        return saved.stream()
                .map(mapping -> new MappingRes(mapping.getSourceHeader(), mapping.getTargetField()))
                .toList();
    }
}
