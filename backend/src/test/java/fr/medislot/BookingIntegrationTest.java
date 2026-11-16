package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;

import fr.medislot.application.BookingService;
import fr.medislot.repository.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.concurrent.*;

@SpringBootTest
@Testcontainers
class BookingIntegrationTest {
    @Container
    static PostgreSQLContainer db =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:18@sha256:2ea8630df176ad090ec92136dee850397c5af8781e4bcf2e4162593691c61bbc")
                            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", db::getJdbcUrl);
        r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
    }

    @Autowired BookingService booking;
    @Autowired SlotRepository slots;
    @Autowired AuditRepository audit;

    @Test
    void concurrentBookingsProduceOneWinner() throws Exception {
        Long slot =
                slots.available(1L, Instant.now(), Instant.now().plus(Duration.ofDays(14)))
                        .getFirst()
                        .id;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> task =
                    () -> {
                        start.await();
                        try {
                            booking.book("patient@medislot.test", slot);
                            return 201;
                        } catch (ResponseStatusException e) {
                            return e.getStatusCode().value();
                        }
                    };
            var a = pool.submit(task);
            var b = pool.submit(task);
            start.countDown();
            var codes =
                    new java.util.ArrayList<>(
                            java.util.List.of(
                                    a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)));
            codes.sort(Integer::compareTo);
            assertEquals(java.util.List.of(201, 409), codes);
        }
    }

    @Test
    void anotherPatientCannotCancelAndCancellationKeepsHistory() {
        Long slot =
                slots.available(2L, Instant.now(), Instant.now().plus(Duration.ofDays(14)))
                        .getFirst()
                        .id;
        var a = booking.book("patient@medislot.test", slot);
        assertEquals(
                404,
                assertThrows(
                                ResponseStatusException.class,
                                () -> booking.cancel("autre@medislot.test", a.id))
                        .getStatusCode()
                        .value());
        booking.cancel("patient@medislot.test", a.id);
        assertEquals(2, audit.findByAppointmentIdOrderByCreatedAtAsc(a.id).size());
        assertTrue(
                slots.available(2L, Instant.now(), Instant.now().plus(Duration.ofDays(14))).stream()
                        .anyMatch(s -> s.id.equals(slot)));
    }
}
