package com.devanshbhosale.marginwatch.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devanshbhosale.marginwatch.dto.DashboardRes;
import com.devanshbhosale.marginwatch.service.DashboardService;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard")
    public DashboardRes dashboard() {
        return dashboardService.load();
    }
}
