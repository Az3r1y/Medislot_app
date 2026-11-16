package fr.medislot.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import fr.medislot.application.BookingService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class EventControllerTest {
    @org.junit.jupiter.api.BeforeEach
    void selectClinic() {
        fr.medislot.tenant.TenantContext.set(1L);
    }

    @org.junit.jupiter.api.AfterEach
    void clearClinic() {
        fr.medislot.tenant.TenantContext.clear();
    }

    @Test
    void returns503WithoutStreamWhenFull() {
        var c = new EventController(mock(BookingService.class), 2);
        assertThat(c.connect(null, new MockHttpServletResponse())).isNotNull();
        assertThat(c.connect(null, new MockHttpServletResponse())).isNotNull();
        var full = new MockHttpServletResponse();
        assertThat(c.connect(null, full)).isNull();
        assertThat(full.getStatus()).isEqualTo(503);
        assertThat(full.getHeader("Retry-After")).isEqualTo("30");
    }
}
