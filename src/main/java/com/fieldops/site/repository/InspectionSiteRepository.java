package com.fieldops.site.repository;

import com.fieldops.site.domain.InspectionSite;
import com.fieldops.site.domain.SiteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InspectionSiteRepository extends JpaRepository<InspectionSite, UUID> {

    /**
     * Overrides the base findById with an entity graph so {@code site.getClient()} is safe to read
     * after this method returns (open-in-view is disabled; mappers run outside the transaction).
     */
    @Override
    @EntityGraph(attributePaths = "client")
    Optional<InspectionSite> findById(UUID id);

    @EntityGraph(attributePaths = "client")
    Page<InspectionSite> findByClientId(UUID clientId, Pageable pageable);

    @EntityGraph(attributePaths = "client")
    Page<InspectionSite> findByClientIdAndStatus(UUID clientId, SiteStatus status, Pageable pageable);
}
