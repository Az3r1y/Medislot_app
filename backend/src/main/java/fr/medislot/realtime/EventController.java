package fr.medislot.realtime;

import fr.medislot.application.*;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;

@RestController
public class EventController {
    private record Subscriber(SseEmitter emitter, Long patient, Long clinic) {}

    private final Set<Subscriber> clients = ConcurrentHashMap.newKeySet();
    private final BookingService booking;
    private final int maxClients;

    // Plafond global, en plus de la limite par IP de Nginx.
    public EventController(
            BookingService booking,
            @org.springframework.beans.factory.annotation.Value("${medislot.sse.max-clients:500}")
                    int maxClients) {
        this.booking = booking;
        this.maxClients = maxClients;
    }

    @GetMapping(value = "/api/events", produces = "text/event-stream")
    public SseEmitter publicEvents(jakarta.servlet.http.HttpServletResponse res) {
        return connect(null, res);
    }

    @GetMapping(value = "/api/notifications/events", produces = "text/event-stream")
    public SseEmitter privateEvents(Principal p, jakarta.servlet.http.HttpServletResponse res) {
        return connect(booking.patient(p.getName()), res);
    }

    // Plein : 503 sans corps, EventSource réessaie seul.
    SseEmitter connect(Long patient, jakarta.servlet.http.HttpServletResponse res) {
        if (clients.size() >= maxClients) {
            res.setStatus(503);
            res.setHeader("Retry-After", "30");
            return null;
        }
        SseEmitter emitter = new SseEmitter(120000L);
        Subscriber s =
                new Subscriber(emitter, patient, fr.medislot.tenant.TenantContext.required());
        clients.add(s);
        emitter.onCompletion(() -> clients.remove(s));
        emitter.onTimeout(
                () -> {
                    clients.remove(s);
                    emitter.complete();
                });
        emitter.onError(e -> clients.remove(s));
        send(s, "ready", Map.of("resync", true));
        return emitter;
    }

    private void send(Subscriber s, String name, Object data) {
        try {
            s.emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception ex) {
            clients.remove(s);
            s.emitter.complete();
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(BookingChanged event) {
        for (Subscriber s : clients) {
            if (!s.clinic.equals(event.clinicId())) continue;
            if (s.patient == null)
                send(s, "slots-changed", Map.of("professionalIds", event.professionalIds()));
            else if (s.patient.equals(event.patientId()))
                send(s, "notifications-changed", Map.of("refresh", true));
        }
    }

    @Scheduled(fixedDelay = 20000)
    public void heartbeat() {
        for (Subscriber s : clients) send(s, "heartbeat", Map.of("alive", true));
    }
}
