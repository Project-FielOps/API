package com.fieldops.dashboard.controller;

import com.fieldops.dashboard.application.DashboardService;
import com.fieldops.dashboard.dto.DashboardSummaryResponse;
import com.fieldops.auth.security.FieldOpsUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@AuthenticationPrincipal FieldOpsUserDetails principal) {
        return dashboardService.summary(principal.getUser());
    }
}
