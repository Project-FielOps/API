package com.fieldops.site.domain;

import com.fieldops.client.domain.Client;
import com.fieldops.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "inspection_site")
@Getter
@Setter
@NoArgsConstructor
public class InspectionSite extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false)
    private String name;

    private String description;

    private String addressLine;
    private String city;
    private String state;
    private String postalCode;

    private BigDecimal latitude;
    private BigDecimal longitude;

    private String contactName;
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SiteStatus status = SiteStatus.ACTIVE;

    public boolean isActive() {
        return status == SiteStatus.ACTIVE;
    }
}
