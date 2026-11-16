package fr.medislot.calendar;

import fr.medislot.domain.Appointment;
import fr.medislot.domain.Professional;
import fr.medislot.domain.Slot;
import fr.medislot.repository.ProfessionalRepository;
import fr.medislot.repository.SlotRepository;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class CalendarService {
    private final SlotRepository slots;
    private final ProfessionalRepository professionals;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    public CalendarService(SlotRepository slots, ProfessionalRepository professionals) {
        this.slots = slots;
        this.professionals = professionals;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n")
                .replace(";", "\\;")
                .replace(",", "\\,");
    }

    // Les lignes de continuation commencent par un espace, inclus dans la limite de 75 octets.
    private static void line(StringBuilder out, String value) {
        int bytes = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            String character = new String(Character.toChars(cp));
            int length = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + length > 75) {
                out.append("\r\n ");
                bytes = 1;
            }
            out.append(character);
            bytes += length;
            i += Character.charCount(cp);
        }
        out.append("\r\n");
    }

    public byte[] render(java.util.List<Appointment> appointments) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//MediSlot//Calendrier//FR");
        line(out, "CALSCALE:GREGORIAN");
        for (Appointment a : appointments) {
            Slot s = slots.findById(a.slotId).orElseThrow();
            Professional p = professionals.findById(s.professionalId).orElseThrow();
            Instant updated = a.calendarUpdatedAt == null ? a.createdAt : a.calendarUpdatedAt;
            line(out, "BEGIN:VEVENT");
            line(out, "UID:appointment-" + a.id + "@medislot");
            line(out, "DTSTAMP:" + DATE.format(updated));
            line(out, "CREATED:" + DATE.format(a.createdAt));
            line(out, "LAST-MODIFIED:" + DATE.format(updated));
            line(out, "SEQUENCE:" + a.calendarSequence);
            line(out, "DTSTART:" + DATE.format(s.startsAt));
            line(out, "DTEND:" + DATE.format(s.endsAt));
            line(out, "SUMMARY:" + escape("Rendez-vous avec " + p.name));
            line(
                    out,
                    "DESCRIPTION:"
                            + escape("Rendez-vous avec " + p.name + " (" + p.specialty + ")"));
            line(out, "LOCATION:" + escape(p.address + ", " + p.city));
            line(out, "STATUS:" + ("CANCELLED".equals(a.status) ? "CANCELLED" : "CONFIRMED"));
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
