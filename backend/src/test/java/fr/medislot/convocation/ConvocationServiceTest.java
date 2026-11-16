package fr.medislot.convocation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fr.medislot.domain.*;
import fr.medislot.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

class ConvocationServiceTest {
    UserRepository users = mock(UserRepository.class);
    AppointmentRepository appointments = mock(AppointmentRepository.class);
    SlotRepository slots = mock(SlotRepository.class);
    ProfessionalRepository professionals = mock(ProfessionalRepository.class);
    PdfRenderer renderer = mock(PdfRenderer.class);
    ConvocationService service =
            new ConvocationService(users, appointments, slots, professionals, renderer);

    @BeforeEach
    void setUp() {
        user(1L, "camille@test");
        user(2L, "autre@test");
        var a = new Appointment();
        a.id = 12L;
        a.patientId = 1L;
        a.slotId = 5L;
        a.status = "CONFIRMED";
        when(appointments.findById(12L)).thenReturn(Optional.of(a));
        var s = new Slot();
        s.id = 5L;
        s.professionalId = 3L;
        s.startsAt = Instant.parse("2026-01-15T08:30:00Z");
        when(slots.findById(5L)).thenReturn(Optional.of(s));
        var p = new Professional();
        p.id = 3L;
        p.name = "Hélène Durand";
        p.specialty = "cardiology";
        p.address = "12 rue de la République";
        p.city = "Lyon";
        when(professionals.findById(3L)).thenReturn(Optional.of(p));
        when(renderer.render(any(), any())).thenReturn(new byte[] {'%'});
    }

    void user(Long id, String email) {
        var u = new AppUser();
        u.id = id;
        u.email = email;
        when(users.findByEmail(email)).thenReturn(Optional.of(u));
    }

    @Test
    void otherPatientGets404AndNoPdfIsRendered() {
        assertThatThrownBy(() -> service.build("autre@test", 12L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        e ->
                                assertThat(((ResponseStatusException) e).getStatusCode().value())
                                        .isEqualTo(404))
                .hasMessageContaining(ConvocationService.NOT_FOUND);
        verifyNoInteractions(renderer);
    }

    @Test
    void unknownAppointmentGets404() {
        assertThatThrownBy(() -> service.build("camille@test", 999L))
                .satisfies(
                        e ->
                                assertThat(((ResponseStatusException) e).getStatusCode().value())
                                        .isEqualTo(404));
        verifyNoInteractions(renderer);
    }

    @Test
    void cancelledAppointmentGets409WithoutPdf() {
        appointments.findById(12L).get().status = "CANCELLED";
        assertThatThrownBy(() -> service.build("camille@test", 12L))
                .satisfies(
                        e ->
                                assertThat(((ResponseStatusException) e).getStatusCode().value())
                                        .isEqualTo(409))
                .hasMessageContaining(ConvocationService.CANCELLED);
        verifyNoInteractions(renderer);
    }

    @Test
    void ownerGetsPdfWithFormattedData() {
        assertThat(service.build("camille@test", 12L)).isNotEmpty();
        verify(renderer)
                .render(
                        "convocation",
                        Map.of(
                                "c",
                                new ConvocationView(
                                        12L,
                                        "jeudi 15 janvier 2026",
                                        "9 h 30",
                                        "Hélène Durand",
                                        "Cardiologie",
                                        "12 rue de la République",
                                        "Lyon")));
    }

    @Test
    void filenameContainsOnlyTheNumber() {
        assertThat(ConvocationService.filename(12L)).isEqualTo("convocation-12.pdf");
    }

    // Paris : UTC+1 en hiver, UTC+2 en été ; le conteneur tourne en UTC.
    @Test
    void parisTimeInWinter() {
        assertThat(ConvocationFormats.time(Instant.parse("2026-01-15T08:30:00Z")))
                .isEqualTo("9 h 30");
        assertThat(ConvocationFormats.day(Instant.parse("2026-01-15T08:30:00Z")))
                .isEqualTo("jeudi 15 janvier 2026");
    }

    @Test
    void parisTimeInSummer() {
        assertThat(ConvocationFormats.time(Instant.parse("2026-07-15T07:30:00Z")))
                .isEqualTo("9 h 30");
    }

    @Test
    void dayChangesAtParisMidnight() {
        // 23 h 30 UTC le 14 juillet, c'est déjà le 15 à Paris.
        assertThat(ConvocationFormats.day(Instant.parse("2026-07-14T23:30:00Z")))
                .isEqualTo("mercredi 15 juillet 2026");
        assertThat(ConvocationFormats.time(Instant.parse("2026-07-14T23:30:00Z")))
                .isEqualTo("1 h 30");
    }
}
