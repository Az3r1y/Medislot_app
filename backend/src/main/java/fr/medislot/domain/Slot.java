package fr.medislot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "slot")
public class Slot extends fr.medislot.tenant.ClinicOwned {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long professionalId;
    public java.time.Instant startsAt;
    public java.time.Instant endsAt;
}
