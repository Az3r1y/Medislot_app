package fr.medislot.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.TenantId;

@MappedSuperclass
public abstract class ClinicOwned {
    @TenantId
    @Column(name = "clinic_id", nullable = false, updatable = false)
    public Long clinicId;
}
