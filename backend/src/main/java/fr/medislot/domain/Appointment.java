package fr.medislot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "appointment")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long patientId;
    public Long slotId;
    public String status;
    public java.time.Instant createdAt;
    public int calendarSequence;
    public java.time.Instant calendarUpdatedAt;
    public String abExperiment;
    public String abVariant;
    public java.util.UUID abSubjectId;
}
