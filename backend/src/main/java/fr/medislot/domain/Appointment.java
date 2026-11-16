package fr.medislot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "appointment")
public class Appointment extends fr.medislot.tenant.ClinicOwned {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long patientId;
    public Long slotId;
    public String status;
    public java.time.Instant createdAt;
    public String abExperiment;
    public String abVariant;
    public java.util.UUID abSubjectId;
}
