package com.fieldops.dashboard.application;

import com.fieldops.dashboard.dto.DashboardSummaryResponse;
import com.fieldops.inspection.domain.InspectionStatus;
import com.fieldops.inspection.repository.InspectionRepository;
import com.fieldops.review.domain.NonConformitySeverity;
import com.fieldops.review.repository.NonConformityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<InspectionStatus> OPEN_STATUSES = List.of(
            InspectionStatus.ASSIGNED, InspectionStatus.IN_PROGRESS, InspectionStatus.REJECTED);

    private final InspectionRepository inspectionRepository;
    private final NonConformityRepository nonConformityRepository;

    public DashboardSummaryResponse summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (InspectionStatus status : InspectionStatus.values()) {
            byStatus.put(status.name(), inspectionRepository.countByStatus(status));
        }
        long overdue = inspectionRepository.countByStatusInAndScheduledForBefore(OPEN_STATUSES, Instant.now());
        long awaitingReview = inspectionRepository.countByStatus(InspectionStatus.SUBMITTED)
                + inspectionRepository.countByStatus(InspectionStatus.UNDER_REVIEW);

        Map<String, Long> ncBySeverity = new LinkedHashMap<>();
        for (NonConformitySeverity severity : NonConformitySeverity.values()) {
            ncBySeverity.put(severity.name(), nonConformityRepository.countBySeverity(severity));
        }
        return new DashboardSummaryResponse(byStatus, overdue, awaitingReview, ncBySeverity);
    }
}
