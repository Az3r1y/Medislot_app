package fr.medislot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import fr.medislot.domain.*;
import fr.medislot.repository.*;

import net.fortuna.ical4j.data.CalendarBuilder;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

class CalendarServiceTest {
    @Test
    void parsesFoldedUtf8AndKeepsUidAcrossChanges() throws Exception {
        var slots = mock(SlotRepository.class);
        var professionals = mock(ProfessionalRepository.class);
        var slot = new Slot();
        slot.id = 1L;
        slot.professionalId = 2L;
        slot.startsAt = Instant.parse("2026-03-29T00:30:00Z");
        slot.endsAt = Instant.parse("2026-03-29T01:30:00Z");
        var professional = new Professional();
        professional.name = "Élodie, ".repeat(20) + "\\;\nNom";
        professional.specialty = "généraliste";
        professional.address = "1, rue des Écoles";
        professional.city = "Paris";
        when(slots.findById(1L)).thenReturn(Optional.of(slot));
        when(professionals.findById(2L)).thenReturn(Optional.of(professional));
        var appointment = new Appointment();
        appointment.id = 9L;
        appointment.slotId = 1L;
        appointment.status = "CONFIRMED";
        appointment.createdAt = Instant.parse("2026-01-01T12:00:00Z");
        appointment.calendarUpdatedAt = appointment.createdAt;
        var service = new CalendarService(slots, professionals);
        byte[] original = service.render(List.of(appointment));
        var parsed = new CalendarBuilder().build(new ByteArrayInputStream(original));
        assertThat(parsed.getComponents()).hasSize(1);
        String text = new String(original, StandardCharsets.UTF_8);
        for (String line : text.split("\r\n"))
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        assertThat(text)
                .contains(
                        "UID:appointment-9@medislot\r\n",
                        "DTSTART:20260329T003000Z",
                        "DTEND:20260329T013000Z",
                        "SEQUENCE:0",
                        "STATUS:CONFIRMED",
                        "\\,",
                        "\\;",
                        "\\n",
                        "\\\\");
        appointment.status = "CANCELLED";
        appointment.calendarSequence = 1;
        appointment.calendarUpdatedAt = Instant.parse("2026-01-02T12:00:00Z");
        byte[] cancelled = service.render(List.of(appointment));
        new CalendarBuilder().build(new ByteArrayInputStream(cancelled));
        assertThat(new String(cancelled, StandardCharsets.UTF_8))
                .contains(
                        "UID:appointment-9@medislot",
                        "SEQUENCE:1",
                        "STATUS:CANCELLED",
                        "LAST-MODIFIED:20260102T120000Z");
    }
}
