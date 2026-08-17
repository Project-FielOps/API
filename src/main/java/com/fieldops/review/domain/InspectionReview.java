package com.fieldops.review.domain;

import com.fieldops.inspection.domain.Inspection;
import com.fieldops.shared.domain.BaseEntity;
import com.fieldops.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One row per review cycle/decision (spec 10.11) - never overwritten, so the full approval history is preserved. */
@Entity
@Table(name = "inspection_review")
@Getter
@Setter
@NoArgsConstructor
public class InspectionReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewDecision decision;

    private String reason;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(nullable = false)
    private Instant reviewedAt;

    @Column(nullable = false)
    private Integer reviewCycle;
}
