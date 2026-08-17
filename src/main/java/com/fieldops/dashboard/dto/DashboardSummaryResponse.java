package com.fieldops.dashboard.dto;

import java.util.Map;

public record DashboardSummaryResponse(
        Map<String, Long> inspectionsByStatus,
        long overdueCount,
        long awaitingReviewCount,
        Map<String, Long> nonConformitiesBySeverity
) {
}
