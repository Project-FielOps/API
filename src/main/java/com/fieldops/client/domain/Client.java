package com.fieldops.client.domain;

import com.fieldops.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "client")
@Getter
@Setter
@NoArgsConstructor
public class Client extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String legalName;

    private String document;

    private String email;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientStatus status = ClientStatus.ACTIVE;

    public boolean isActive() {
        return status == ClientStatus.ACTIVE;
    }
}
