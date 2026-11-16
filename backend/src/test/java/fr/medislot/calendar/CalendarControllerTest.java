package fr.medislot.calendar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fr.medislot.application.BookingService;
import fr.medislot.domain.*;
import fr.medislot.repository.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

class CalendarControllerTest {
    BookingService booking = mock(BookingService.class);
    AppointmentRepository appointments = mock(AppointmentRepository.class);
    CalendarService calendar = mock(CalendarService.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CalendarController controller = new CalendarController(booking, appointments, calendar, jdbc);
    Principal patient = () -> "patient@test";

    @Test
    void exportRejectsAnotherPatient() {
        var a = new Appointment();
        a.patientId = 2L;
        when(booking.patient(patient.getName())).thenReturn(1L);
        when(appointments.findById(3L)).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> controller.appointment(patient, 3L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(calendar);
    }

    @Test
    void generatesAndRevokesHashedTokensAndHandlesConditionalFeed() throws Exception {
        when(booking.patient(patient.getName())).thenReturn(1L);
        String token = controller.create(patient).getBody().token();
        assertThat(token).hasSize(43);
        String hash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(token.getBytes(StandardCharsets.US_ASCII)));
        verify(jdbc).update(contains("INSERT INTO calendar_subscription"), eq(hash), eq(1L));
        String next = controller.create(patient).getBody().token();
        assertThat(next).isNotEqualTo(token);
        when(jdbc.queryForList(contains("where token_hash=?"), eq(Long.class), eq(hash)))
                .thenReturn(List.of(1L));
        when(appointments.findByPatientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(calendar.render(List.of())).thenReturn("calendar".getBytes(StandardCharsets.UTF_8));
        var response =
                controller.feed(
                        token,
                        new ServletWebRequest(
                                new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.addHeader("If-None-Match", response.getHeaders().getETag());
        assertThat(
                        controller
                                .feed(
                                        token,
                                        new ServletWebRequest(
                                                request, new MockHttpServletResponse()))
                                .getStatusCode()
                                .value())
                .isEqualTo(304);
        when(jdbc.queryForList(contains("where token_hash=?"), eq(Long.class), eq(hash)))
                .thenReturn(List.of());
        assertThatThrownBy(
                        () ->
                                controller.feed(
                                        token, new ServletWebRequest(new MockHttpServletRequest())))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        controller.revoke(patient);
        verify(jdbc).update(contains("DELETE FROM calendar_subscription"), eq(1L));
    }
}
