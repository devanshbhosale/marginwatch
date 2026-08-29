package com.devanshbhosale.marginwatch.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.devanshbhosale.marginwatch.domain.AuditLog;
import com.devanshbhosale.marginwatch.domain.MarginPolicy;
import com.devanshbhosale.marginwatch.domain.User;
import com.devanshbhosale.marginwatch.domain.UserRole;
import com.devanshbhosale.marginwatch.dto.PolicyReq;
import com.devanshbhosale.marginwatch.dto.PolicyRes;
import com.devanshbhosale.marginwatch.exception.ApiException;
import com.devanshbhosale.marginwatch.repository.AuditLogRepository;
import com.devanshbhosale.marginwatch.repository.MarginPolicyRepository;

import jakarta.validation.Valid;

@RestController
public class PolicyController {

    private final MarginPolicyRepository policyRepository;
    private final AuditLogRepository auditRepository;

    public PolicyController(MarginPolicyRepository policyRepository, AuditLogRepository auditRepository) {
        this.policyRepository = policyRepository;
        this.auditRepository = auditRepository;
    }

    @GetMapping("/api/policies")
    public List<PolicyRes> list() {
        return policyRepository.findAllByOrderByIdAsc().stream()
                .map(PolicyController::toRes)
                .toList();
    }

    @PostMapping("/api/policies")
    @Transactional
    public org.springframework.http.ResponseEntity<PolicyRes> create(@Valid @RequestBody PolicyReq request,
                                                                     @AuthenticationPrincipal User actor) {
        requireAdmin(actor);
        String category = normalizeCategory(request.category());
        if (category == null && policyRepository.findGlobal().isPresent()) {
            throw new ApiException(409, "A global policy already exists");
        }
        if (category != null && policyRepository.findByCategory(category).isPresent()) {
            throw new ApiException(409, "A policy for category " + category + " already exists");
        }
        MarginPolicy policy = new MarginPolicy();
        policy.setCategory(category);
        policy.setMinMarginPct(request.minMarginPct());
        policy = policyRepository.save(policy);
        audit(actor, policy, null);
        return org.springframework.http.ResponseEntity.status(HttpStatus.CREATED).body(toRes(policy));
    }

    @PutMapping("/api/policies/{id}")
    @Transactional
    public PolicyRes update(@PathVariable Long id, @Valid @RequestBody PolicyReq request,
                            @AuthenticationPrincipal User actor) {
        requireAdmin(actor);
        MarginPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Policy not found"));
        String category = normalizeCategory(request.category());
        MarginPolicy clash = category == null
                ? policyRepository.findGlobal().orElse(null)
                : policyRepository.findByCategory(category).orElse(null);
        if (clash != null && !clash.getId().equals(policy.getId())) {
            throw new ApiException(409, category == null
                    ? "A global policy already exists"
                    : "A policy for category " + category + " already exists");
        }
        BigDecimal old = policy.getMinMarginPct();
        policy.setCategory(category);
        policy.setMinMarginPct(request.minMarginPct());
        policy = policyRepository.save(policy);
        audit(actor, policy, old);
        return toRes(policy);
    }

    private static void requireAdmin(User actor) {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only ADMIN can manage policies");
        }
    }

    private static String normalizeCategory(String category) {
        return category == null || category.isBlank() ? null : category.trim();
    }

    private void audit(User actor, MarginPolicy policy, java.math.BigDecimal old) {
        AuditLog entry = new AuditLog();
        entry.setActorEmail(actor.getEmail());
        entry.setAction("POLICY_UPDATED");
        entry.setEntityRef("MARGIN_POLICY:" + policy.getId());
        entry.setDetails("category=" + (policy.getCategory() == null ? "(global)" : policy.getCategory())
                + "; minMarginPct=" + policy.getMinMarginPct()
                + (old != null ? " (was " + old + ")" : ""));
        entry.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        auditRepository.save(entry);
    }

    static PolicyRes toRes(MarginPolicy policy) {
        return new PolicyRes(policy.getId(), policy.getCategory(), policy.getMinMarginPct());
    }
}
