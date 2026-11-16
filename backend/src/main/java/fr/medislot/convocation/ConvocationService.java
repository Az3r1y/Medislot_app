package fr.medislot.convocation;

import fr.medislot.domain.Appointment;
import fr.medislot.repository.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class ConvocationService {
    static final String NOT_FOUND = "Rendez-vous introuvable.";
    static final String CANCELLED = "Ce rendez-vous est annulé : il n'a plus de convocation.";
    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final SlotRepository slots;
    private final ProfessionalRepository professionals;
    private final PdfRenderer renderer;

    public ConvocationService(
            UserRepository u,
            AppointmentRepository a,
            SlotRepository s,
            ProfessionalRepository p,
            PdfRenderer r) {
        users = u;
        appointments = a;
        slots = s;
        professionals = p;
        renderer = r;
    }

    // Les droits sont vérifiés avant de fabriquer le PDF.
    @Transactional(readOnly = true)
    public byte[] build(String email, Long id) {
        Long patient = users.findByEmail(email).map(u -> u.id).orElseThrow(() -> notFound());
        // 404 plutôt que 403 : ne pas révéler que le rendez-vous existe.
        Appointment a =
                appointments
                        .findById(id)
                        .filter(x -> x.patientId.equals(patient))
                        .orElseThrow(() -> notFound());
        if ("CANCELLED".equals(a.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, CANCELLED);
        var s = slots.findById(a.slotId).orElseThrow(() -> notFound());
        var p = professionals.findById(s.professionalId).orElseThrow(() -> notFound());
        var view =
                new ConvocationView(
                        a.id,
                        ConvocationFormats.day(s.startsAt),
                        ConvocationFormats.time(s.startsAt),
                        p.name,
                        ConvocationFormats.specialty(p.specialty),
                        p.address,
                        p.city);
        return renderer.render("convocation", Map.of("c", view));
    }

    // Ni nom, ni spécialité, ni date dans le nom du fichier.
    public static String filename(Long id) {
        return "convocation-" + id + ".pdf";
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }
}
