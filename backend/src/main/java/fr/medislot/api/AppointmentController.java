package fr.medislot.api;

import fr.medislot.application.AbService;
import fr.medislot.application.BookingService;
import fr.medislot.domain.*;
import fr.medislot.repository.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;

@RestController
@RequestMapping("/api")
public class AppointmentController {
    private final BookingService service;
    private final AbService ab;
    private final AppointmentRepository appointments;
    private final SlotRepository slots;
    private final ProfessionalRepository professionals;
    private final AuditRepository audit;
    private final NotificationRepository notifications;

    public AppointmentController(
            BookingService s,
            AbService b,
            AppointmentRepository a,
            SlotRepository t,
            ProfessionalRepository p,
            AuditRepository h,
            NotificationRepository n) {
        service = s;
        ab = b;
        appointments = a;
        slots = t;
        professionals = p;
        audit = h;
        notifications = n;
    }

    public record BookingRequest(@NotNull @Positive Long slotId) {}

    public record AppointmentDto(
            Long id,
            Long slotId,
            Long professionalId,
            String professionalName,
            String specialty,
            String address,
            String city,
            Instant startsAt,
            Instant endsAt,
            String status) {}

    public record HistoryDto(String action, Instant createdAt) {}

    public record NotificationDto(Long id, String message, Instant createdAt) {}

    private AppointmentDto dto(Appointment a) {
        Slot s = slots.findById(a.slotId).orElseThrow();
        Professional p = professionals.findById(s.professionalId).orElseThrow();
        return new AppointmentDto(
                a.id,
                s.id,
                p.id,
                p.name,
                p.specialty,
                p.address,
                p.city,
                s.startsAt,
                s.endsAt,
                a.status);
    }

    @GetMapping("/appointments")
    public java.util.List<AppointmentDto> list(Principal p) {
        return appointments
                .findByPatientIdOrderByCreatedAtDesc(service.patient(p.getName()))
                .stream()
                .map(this::dto)
                .toList();
    }

    // X-AB-Measure n'est envoyé que si le front a reçu une affectation.
    @PostMapping("/appointments")
    public ResponseEntity<AppointmentDto> book(
            Principal p,
            @Valid @RequestBody BookingRequest r,
            @RequestHeader(name = "X-AB-Measure", required = false) String measure,
            @CookieValue(name = AbController.COOKIE, required = false) String visitor) {
        var attribution =
                "1".equals(measure)
                        ? ab.attribution(AbController.subject(visitor))
                        : java.util.Optional.<AbService.Assignment>empty();
        var a = service.book(p.getName(), r.slotId(), attribution);
        return ResponseEntity.created(java.net.URI.create("/api/appointments/" + a.id))
                .body(dto(a));
    }

    @GetMapping("/appointments/{id}")
    public AppointmentDto one(Principal p, @PathVariable Long id) {
        return dto(owned(p, id));
    }

    private Appointment owned(Principal p, Long id) {
        return appointments
                .findById(id)
                .filter(a -> a.patientId.equals(service.patient(p.getName())))
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Rendez-vous introuvable."));
    }

    @PostMapping("/appointments/{id}/cancel")
    public AppointmentDto cancel(Principal p, @PathVariable Long id) {
        return dto(service.cancel(p.getName(), id));
    }

    @PatchMapping("/appointments/{id}")
    public AppointmentDto move(
            Principal p, @PathVariable Long id, @Valid @RequestBody BookingRequest r) {
        return dto(service.reschedule(p.getName(), id, r.slotId()));
    }

    @GetMapping("/appointments/{id}/history")
    public java.util.List<HistoryDto> history(Principal p, @PathVariable Long id) {
        owned(p, id);
        return audit.findByAppointmentIdOrderByCreatedAtAsc(id).stream()
                .map(h -> new HistoryDto(h.action, h.createdAt))
                .toList();
    }

    @GetMapping("/notifications")
    public java.util.List<NotificationDto> notifications(Principal p) {
        return notifications
                .findTop30ByPatientIdOrderByCreatedAtDesc(service.patient(p.getName()))
                .stream()
                .map(n -> new NotificationDto(n.id, n.message, n.createdAt))
                .toList();
    }
}
