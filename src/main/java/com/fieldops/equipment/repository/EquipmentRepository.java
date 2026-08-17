package com.fieldops.equipment.repository;

import com.fieldops.equipment.domain.Equipment;
import com.fieldops.equipment.domain.EquipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {

    /**
     * Fetches site and site.client eagerly so EquipmentMapper (which runs after this transaction
     * closes, since open-in-view is disabled) can safely read their names.
     */
    @Override
    @EntityGraph(attributePaths = {"site", "site.client"})
    Optional<Equipment> findById(UUID id);

    @EntityGraph(attributePaths = {"site", "site.client"})
    Page<Equipment> findBySiteId(UUID siteId, Pageable pageable);

    @EntityGraph(attributePaths = {"site", "site.client"})
    Page<Equipment> findBySiteIdAndStatus(UUID siteId, EquipmentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"site", "site.client"})
    Optional<Equipment> findByQrCode(String qrCode);

    boolean existsByQrCode(String qrCode);
}
