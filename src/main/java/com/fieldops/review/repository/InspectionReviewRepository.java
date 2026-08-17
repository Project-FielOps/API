package com.fieldops.review.repository;

import com.fieldops.review.domain.InspectionReview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InspectionReviewRepository extends JpaRepository<InspectionReview, UUID> {

    /** ReviewMapper reads reviewer.getName() after the transaction closes, hence the entity graph. */
    @EntityGraph(attributePaths = "reviewer")
    List<InspectionReview> findByInspectionIdOrderByReviewCycleAsc(UUID inspectionId);

    int countByInspectionId(UUID inspectionId);
}
