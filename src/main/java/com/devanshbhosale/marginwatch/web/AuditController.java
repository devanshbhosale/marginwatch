package com.devanshbhosale.marginwatch.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devanshbhosale.marginwatch.dto.AuditRes;
import com.devanshbhosale.marginwatch.dto.PageRes;
import com.devanshbhosale.marginwatch.repository.AuditLogRepository;

@RestController
public class AuditController {

    private final AuditLogRepository auditRepository;

    public AuditController(AuditLogRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping("/api/audit")
    public PageRes<AuditRes> list(@RequestParam(required = false) String actor,
                                  @RequestParam(required = false) String action,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        var result = auditRepository.search(blankToNull(actor), blankToNull(action),
                PageRequest.of(Math.max(page, 0), pageSize));
        return PageRes.of(result, result.map(entry -> new AuditRes(entry.getId(), entry.getActorEmail(),
                entry.getAction(), entry.getEntityRef(), entry.getDetails(), entry.getCreatedAt())).getContent());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
