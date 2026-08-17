package com.fieldops.review.application;

import com.fieldops.inspection.application.InspectionService;
import com.fieldops.inspection.domain.Inspection;
import com.fieldops.review.domain.InspectionReview;
import com.fieldops.review.domain.ReviewDecision;
import com.fieldops.review.repository.InspectionReviewRepository;
import com.fieldops.shared.exception.NotFoundException;
import com.fieldops.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private final InspectionReviewRepository reviewRepository;
    private final InspectionService inspectionService;

    public Inspection beginReview(UUID inspectionId, User actor) {
        return inspectionService.beginReview(inspectionId, actor);
    }

    public Inspection approve(UUID inspectionId, String comments, User actor) {
        Inspection inspection = inspectionService.approve(inspectionId, actor);
        recordReview(inspection, actor, ReviewDecision.APPROVED, null, comments);
        return inspection;
    }

    public Inspection reject(UUID inspectionId, String reason, String comments, User actor) {
        Inspection inspection = inspectionService.reject(inspectionId, actor);
        recordReview(inspection, actor, ReviewDecision.REJECTED, reason, comments);
        return inspection;
    }

    private void recordReview(Inspection inspection, User reviewer, ReviewDecision decision, String reason, String comments) {
        int nextCycle = reviewRepository.countByInspectionId(inspection.getId()) + 1;
        InspectionReview review = new InspectionReview();
        review.setInspection(inspection);
        review.setReviewer(reviewer);
        review.setDecision(decision);
        review.setReason(reason);
        review.setComments(comments);
        review.setReviewedAt(Instant.now());
        review.setReviewCycle(nextCycle);
        reviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public List<InspectionReview> listByInspection(UUID inspectionId) {
        return reviewRepository.findByInspectionIdOrderByReviewCycleAsc(inspectionId);
    }

    @Transactional(readOnly = true)
    public InspectionReview getById(UUID id) {
        return reviewRepository.findById(id).orElseThrow(() -> NotFoundException.of("InspectionReview", id));
    }
}
