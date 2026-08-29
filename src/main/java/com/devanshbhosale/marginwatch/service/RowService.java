package com.devanshbhosale.marginwatch.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devanshbhosale.marginwatch.domain.AuditLog;
import com.devanshbhosale.marginwatch.domain.PriceFileRow;
import com.devanshbhosale.marginwatch.domain.Product;
import com.devanshbhosale.marginwatch.domain.RowFlag;
import com.devanshbhosale.marginwatch.domain.RowStatus;
import com.devanshbhosale.marginwatch.domain.User;
import com.devanshbhosale.marginwatch.domain.UserRole;
import com.devanshbhosale.marginwatch.dto.ApplyAllRes;
import com.devanshbhosale.marginwatch.dto.RowRes;
import com.devanshbhosale.marginwatch.exception.ApiException;
import com.devanshbhosale.marginwatch.repository.AuditLogRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRepository;
import com.devanshbhosale.marginwatch.repository.PriceFileRowRepository;
import com.devanshbhosale.marginwatch.repository.ProductRepository;

/**
 * Apply / skip pipeline. Every transition goes through the PENDING-only
 * conditional update, so two operators racing on one row cannot double-apply:
 * the loser of the race gets a 409 (apply/skip) or is skipped silently
 * (apply-all).
 */
@Service
public class RowService {

    private final PriceFileRowRepository rowRepository;
    private final PriceFileRepository fileRepository;
    private final ProductRepository productRepository;
    private final AuditLogRepository auditRepository;
    private final DiffMath diffMath;

    public RowService(PriceFileRowRepository rowRepository,
                      PriceFileRepository fileRepository,
                      ProductRepository productRepository,
                      AuditLogRepository auditRepository,
                      DiffMath diffMath) {
        this.rowRepository = rowRepository;
        this.fileRepository = fileRepository;
        this.productRepository = productRepository;
        this.auditRepository = auditRepository;
        this.diffMath = diffMath;
    }

    @Transactional
    public RowRes apply(Long rowId, String overrideReason, User actor) {
        PriceFileRow row = rowRepository.findById(rowId)
                .orElseThrow(() -> new ApiException(404, "Row not found"));
        if (row.getFlag() != RowFlag.OK && row.getFlag() != RowFlag.BREACH) {
            throw new ApiException(409, "Row is not applicable — skip only");
        }
        if (row.getFlag() == RowFlag.BREACH) {
            if (actor.getRole() != UserRole.ADMIN) {
                throw new ApiException(403, "Applying a breach requires ADMIN");
            }
            if (overrideReason == null || overrideReason.isBlank()) {
                throw new ApiException(422, "An override reason is required to apply a breach");
            }
        }
        if (rowRepository.transitionStatus(row.getId(), RowStatus.PENDING, RowStatus.APPLIED) == 0) {
            throw new ApiException(409, "Row is no longer pending");
        }
        PriceFileRow applied = rowRepository.findById(rowId).orElseThrow();
        Product product = productRepository.findBySkuIgnoreCase(applied.getSku())
                .orElseThrow(() -> new ApiException(409, "The product for this row no longer exists"));
        product.setCurrentCost(applied.getNewCost());
        productRepository.save(product);

        BigDecimal floor = diffMath.floorFor(product.getCategory());
        String details = "sku=" + applied.getSku()
                + "; cost " + applied.getOldCost() + " → " + applied.getNewCost()
                + "; change " + applied.getChangePct() + "%"
                + "; resulting margin " + applied.getResultingMarginPct() + "%"
                + "; floor " + floor;
        if (overrideReason != null && !overrideReason.isBlank()) {
            details += "; override reason: " + overrideReason.trim();
        }
        auditRepository.save(audit(actor, "PRICE_APPLIED", "PRICE_FILE_ROW:" + applied.getId(), details));
        return toRes(applied);
    }

    @Transactional
    public RowRes skip(Long rowId, User actor) {
        if (rowRepository.transitionStatus(rowId, RowStatus.PENDING, RowStatus.SKIPPED) == 0) {
            rowRepository.findById(rowId)
                    .orElseThrow(() -> new ApiException(404, "Row not found"));
            throw new ApiException(409, "Row is no longer pending");
        }
        return toRes(rowRepository.findById(rowId).orElseThrow());
    }

    /**
     * Applies every PENDING OK row, plus PENDING BREACH rows when
     * includeBreaches is set (ADMIN + reason required then). Returns how many
     * PENDING rows were left untouched (breaches excluded, never-applicable
     * flags, rows lost to a race).
     */
    @Transactional
    public ApplyAllRes applyAll(Long fileId, boolean includeBreaches, String overrideReason, User actor) {
        fileRepository.findById(fileId)
                .orElseThrow(() -> new ApiException(404, "File not found"));
        if (includeBreaches) {
            if (actor.getRole() != UserRole.ADMIN) {
                throw new ApiException(403, "Applying breaches requires ADMIN");
            }
            if (overrideReason == null || overrideReason.isBlank()) {
                throw new ApiException(422, "An override reason is required to apply breaches");
            }
        }
        long pendingBefore = rowRepository.countByPriceFileIdAndStatus(fileId, RowStatus.PENDING);
        List<PriceFileRow> pending = rowRepository.findByPriceFileIdAndStatus(fileId, RowStatus.PENDING);

        int applied = 0;
        int breachApplied = 0;
        String reason = overrideReason == null ? null : overrideReason.trim();
        for (PriceFileRow row : pending) {
            boolean breach = row.getFlag() == RowFlag.BREACH;
            if (breach && !includeBreaches) {
                continue;
            }
            if (!breach && row.getFlag() != RowFlag.OK) {
                continue;
            }
            if (rowRepository.transitionStatus(row.getId(), RowStatus.PENDING, RowStatus.APPLIED) == 0) {
                continue;
            }
            productRepository.findBySkuIgnoreCase(row.getSku()).ifPresent(product -> {
                product.setCurrentCost(row.getNewCost());
                productRepository.save(product);
            });
            String details = "sku=" + row.getSku()
                    + "; cost " + row.getOldCost() + " → " + row.getNewCost()
                    + "; change " + row.getChangePct() + "%"
                    + "; resulting margin " + row.getResultingMarginPct() + "%"
                    + "; floor " + diffMath.floorFor(productCategory(row))
                    + (breach && reason != null && !reason.isBlank() ? "; override reason: " + reason : "");
            auditRepository.save(audit(actor, "PRICE_APPLIED", "PRICE_FILE_ROW:" + row.getId(), details));
            if (breach) {
                breachApplied++;
            } else {
                applied++;
            }
        }
        int skipped = (int) (pendingBefore - applied - breachApplied);
        return new ApplyAllRes(applied, breachApplied, skipped);
    }

    private String productCategory(PriceFileRow row) {
        return productRepository.findBySkuIgnoreCase(row.getSku())
                .map(Product::getCategory).orElse(null);
    }

    private AuditLog audit(User actor, String action, String entityRef, String details) {
        AuditLog entry = new AuditLog();
        entry.setActorEmail(actor.getEmail());
        entry.setAction(action);
        entry.setEntityRef(entityRef);
        entry.setDetails(details);
        entry.setCreatedAt(LocalDateTime.now(FileService.IST));
        return entry;
    }

    public RowRes toRes(PriceFileRow row) {
        Product product = row.getSku() == null ? null
                : productRepository.findBySkuIgnoreCase(row.getSku()).orElse(null);
        BigDecimal selling = product == null ? null : product.getSellingPrice();
        BigDecimal floor = product == null ? null : diffMath.floorFor(product.getCategory());
        return new RowRes(row.getId(), row.getRowNumber(), row.getSku(), row.getProductName(),
                row.getOldCost(), row.getNewCost(), row.getChangePct(), row.getResultingMarginPct(),
                row.getFlag().name(), row.getStatus().name(), row.getRawRow(), row.getNote(),
                selling, floor);
    }

    public com.devanshbhosale.marginwatch.dto.PageRes<RowRes> toPageRes(Page<PriceFileRow> page) {
        return com.devanshbhosale.marginwatch.dto.PageRes.of(page, page.map(this::toRes).getContent());
    }
}
