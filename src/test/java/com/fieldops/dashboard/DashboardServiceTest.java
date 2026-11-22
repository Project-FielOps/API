package com.fieldops.dashboard;

import com.fieldops.dashboard.application.DashboardService;
import com.fieldops.dashboard.dto.DashboardSummaryResponse;
import com.fieldops.inspection.domain.InspectionStatus;
import com.fieldops.inspection.repository.InspectionRepository;
import com.fieldops.review.domain.NonConformitySeverity;
import com.fieldops.review.repository.NonConformityRepository;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardServiceTest {

    @Test
    void supervisorDashboardCountsOnlyAssignedInspectionsAndNonConformities() {
        User supervisor = new User();
        supervisor.setId(UUID.randomUUID());
        supervisor.setRole(Role.SUPERVISOR);
        DashboardService dashboardService = new DashboardService(
                inspectionRepositoryReturning(2L, 1L), nonConformityRepositoryReturning(3L));

        DashboardSummaryResponse response = dashboardService.summary(supervisor);

        assertThat(response.inspectionsByStatus()).containsEntry(InspectionStatus.ASSIGNED.name(), 2L);
        assertThat(response.overdueCount()).isEqualTo(1L);
        assertThat(response.awaitingReviewCount()).isEqualTo(4L);
        assertThat(response.nonConformitiesBySeverity()).containsEntry(NonConformitySeverity.CRITICAL.name(), 3L);
    }

    @Test
    void adminDashboardUsesGlobalCounters() {
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setRole(Role.ADMIN);
        DashboardService dashboardService = new DashboardService(
                inspectionRepositoryReturning(5L, 2L), nonConformityRepositoryReturning(7L));

        DashboardSummaryResponse response = dashboardService.summary(admin);

        assertThat(response.inspectionsByStatus()).containsEntry(InspectionStatus.APPROVED.name(), 5L);
        assertThat(response.overdueCount()).isEqualTo(2L);
        assertThat(response.nonConformitiesBySeverity()).containsEntry(NonConformitySeverity.HIGH.name(), 7L);
    }

    private InspectionRepository inspectionRepositoryReturning(long statusCount, long overdueCount) {
        return (InspectionRepository) Proxy.newProxyInstance(
                InspectionRepository.class.getClassLoader(),
                new Class<?>[]{InspectionRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().contains("StatusInAndScheduledForBefore")) return overdueCount;
                    if (method.getName().startsWith("countBy")) return statusCount;
                    return defaultValue(method.getReturnType());
                });
    }

    private NonConformityRepository nonConformityRepositoryReturning(long count) {
        return (NonConformityRepository) Proxy.newProxyInstance(
                NonConformityRepository.class.getClassLoader(),
                new Class<?>[]{NonConformityRepository.class},
                (proxy, method, args) -> method.getName().startsWith("countBy")
                        ? count : defaultValue(method.getReturnType()));
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        return 0;
    }
}
