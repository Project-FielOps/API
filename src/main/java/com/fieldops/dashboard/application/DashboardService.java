package com.fieldops.dashboard.application;

import com.fieldops.dashboard.dto.DashboardSummaryResponse;
import com.fieldops.inspection.domain.InspectionStatus;
import com.fieldops.inspection.repository.InspectionRepository;
import com.fieldops.review.domain.NonConformitySeverity;
import com.fieldops.review.repository.NonConformityRepository;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
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

    public DashboardSummaryResponse summary(User actor) {
        boolean scopedToSupervisor = actor.getRole() == Role.SUPERVISOR;
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (InspectionStatus status : InspectionStatus.values()) {
            byStatus.put(status.name(), scopedToSupervisor
                    ? inspectionRepository.countBySupervisorIdAndStatus(actor.getId(), status)
                    : inspectionRepository.countByStatus(status));
        }
        Instant now = Instant.now();
        long overdue = scopedToSupervisor
                ? inspectionRepository.countBySupervisorIdAndStatusInAndScheduledForBefore(actor.getId(), OPEN_STATUSES, now)
                : inspectionRepository.countByStatusInAndScheduledForBefore(OPEN_STATUSES, now);
        long awaitingReview = scopedToSupervisor
                ? inspectionRepository.countBySupervisorIdAndStatus(actor.getId(), InspectionStatus.SUBMITTED)
                    + inspectionRepository.countBySupervisorIdAndStatus(actor.getId(), InspectionStatus.UNDER_REVIEW)
                : inspectionRepository.countByStatus(InspectionStatus.SUBMITTED)
                    + inspectionRepository.countByStatus(InspectionStatus.UNDER_REVIEW);

        Map<String, Long> ncBySeverity = new LinkedHashMap<>();
        for (NonConformitySeverity severity : NonConformitySeverity.values()) {
            ncBySeverity.put(severity.name(), scopedToSupervisor
                    ? nonConformityRepository.countByInspectionSupervisorIdAndSeverity(actor.getId(), severity)
                    : nonConformityRepository.countBySeverity(severity));
        }
        return new DashboardSummaryResponse(byStatus, overdue, awaitingReview, ncBySeverity);
    }
}
