package com.fieldops.inspection.repository;

import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.domain.InspectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fieldops.inspection.domain.Priority;
import org.springframework.data.repository.query.Param;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

    /**
     * InspectionMapper reads client/site/equipment/technician/supervisor/templateVersion names after
     * the transaction closes (open-in-view is disabled), so every read path needs this entity graph -
     * otherwise it throws LazyInitializationException.
     */
    @Override
    @EntityGraph(attributePaths = {"client", "site", "equipment", "technician", "supervisor", "templateVersion"})
    Optional<Inspection> findById(UUID id);

    @EntityGraph(attributePaths = {"client", "site", "equipment", "technician", "supervisor", "templateVersion"})
    Page<Inspection> findByTechnicianIdAndStatus(UUID technicianId, InspectionStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "site", "equipment", "technician", "supervisor", "templateVersion"})
    Page<Inspection> findByTechnicianId(UUID technicianId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "site", "equipment", "technician", "supervisor", "templateVersion"})
    Page<Inspection> findBySupervisorIdAndStatus(UUID supervisorId, InspectionStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "site", "equipment", "technician", "supervisor", "templateVersion"})
    Page<Inspection> findBySupervisorId(UUID supervisorId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "site", "equipment", "technician", "supervisor", "templateVersion"})
    Page<Inspection> findByClientId(UUID clientId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "site", "equipment", "technician", "supervisor", "templateVersion"})
    Page<Inspection> findBySiteId(UUID siteId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "site", "equipment", "technician", "supervisor", "templateVersion"})
    Page<Inspection> findByStatus(InspectionStatus status, Pageable pageable);

    @Query("""
            select i from Inspection i
            where (:technicianId is null or i.technician.id = :technicianId)
              and (:supervisorId is null or i.supervisor.id = :supervisorId)
              and (:clientId is null or i.client.id = :clientId)
              and (:siteId is null or i.site.id = :siteId)
              and (:status is null or i.status = :status)
              and (:priority is null or i.priority = :priority)
              and (:scheduledFrom is null or i.scheduledFor >= :scheduledFrom)
              and (:scheduledTo is null or i.scheduledFor <= :scheduledTo)
            """)
    @EntityGraph(attributePaths = {"client", "site", "equipment", "technician", "supervisor", "templateVersion"})
    Page<Inspection> findFiltered(@Param("technicianId") UUID technicianId,
                                  @Param("supervisorId") UUID supervisorId,
                                  @Param("clientId") UUID clientId,
                                  @Param("siteId") UUID siteId,
                                  @Param("status") InspectionStatus status,
                                  @Param("priority") Priority priority,
                                  @Param("scheduledFrom") Instant scheduledFrom,
                                  @Param("scheduledTo") Instant scheduledTo,
                                  Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"client", "site", "equipment", "technician", "supervisor", "templateVersion"})
    Page<Inspection> findAll(Pageable pageable);

    long countByStatus(InspectionStatus status);

    long countByStatusAndScheduledForBefore(InspectionStatus status, Instant before);

    long countByStatusInAndScheduledForBefore(List<InspectionStatus> statuses, Instant before);

    @Query("select i.status as status, count(i) as total from Inspection i group by i.status")
    List<StatusCount> countGroupedByStatus();

    interface StatusCount {
        InspectionStatus getStatus();
        long getTotal();
    }
}
