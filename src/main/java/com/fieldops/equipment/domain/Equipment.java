package com.fieldops.equipment.domain;

import com.fieldops.shared.domain.BaseEntity;
import com.fieldops.site.domain.InspectionSite;
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

import java.time.LocalDate;

@Entity
@Table(name = "equipment")
@Getter
@Setter
@NoArgsConstructor
public class Equipment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private InspectionSite site;

    @Column(nullable = false)
    private String name;

    private String assetNumber;
    private String serialNumber;
    private String manufacturer;
    private String model;
    private String description;

    @Column(nullable = false, unique = true)
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentStatus status = EquipmentStatus.ACTIVE;

    private LocalDate installedAt;

    public boolean isUsable() {
        return status == EquipmentStatus.ACTIVE;
    }
}
