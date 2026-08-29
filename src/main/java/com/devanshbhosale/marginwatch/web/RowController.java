package com.devanshbhosale.marginwatch.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.devanshbhosale.marginwatch.domain.User;
import com.devanshbhosale.marginwatch.dto.ApplyReq;
import com.devanshbhosale.marginwatch.dto.RowRes;
import com.devanshbhosale.marginwatch.service.RowService;

@RestController
public class RowController {

    private final RowService rowService;

    public RowController(RowService rowService) {
        this.rowService = rowService;
    }

    @PostMapping("/api/rows/{id}/apply")
    public RowRes apply(@PathVariable Long id,
                        @RequestBody(required = false) ApplyReq request,
                        @AuthenticationPrincipal User actor) {
        return rowService.apply(id, request == null ? null : request.overrideReason(), actor);
    }

    @PostMapping("/api/rows/{id}/skip")
    public RowRes skip(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return rowService.skip(id, actor);
    }
}
