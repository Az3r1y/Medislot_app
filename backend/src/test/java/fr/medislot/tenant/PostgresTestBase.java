package fr.medislot.tenant;

import static org.assertj.core.api.Assertions.*;

import fr.medislot.application.BookingService;
import fr.medislot.repository.SlotRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PostgresTestBase {
    static final PostgreSQLContainer DB =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:18@sha256:2ea8630df176ad090ec92136dee850397c5af8781e4bcf2e4162593691c61bbc")
                            .asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DB::getJdbcUrl);
        properties.add("spring.datasource.username", () -> "medislot_app");
        properties.add("spring.datasource.password", () -> "medislot-app-local-only");
        properties.add("spring.flyway.url", DB::getJdbcUrl);
        properties.add("spring.flyway.user", DB::getUsername);
        properties.add("spring.flyway.password", DB::getPassword);
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingService bookings;
    @Autowired SlotRepository slots;
    @Autowired PlatformTransactionManager manager;
    final ObjectMapper mapper = new ObjectMapper();
    long appointmentA, appointmentB;

    @BeforeAll
    void fixtures() {
        appointmentA = fixture(1L);
        appointmentB = fixture(2L);
    }

    long fixture(long clinic) {
        TenantContext.set(clinic);
        try {
            return new TransactionTemplate(manager)
                    .execute(
                            status -> {
                                Long professional =
                                        jdbc.queryForObject(
                                                "select min(id) from professional where clinic_id=?",
                                                Long.class,
                                                clinic);
                                Long slot =
                                        slots.available(
                                                        professional,
                                                        Instant.now(),
                                                        Instant.now().plus(Duration.ofDays(14)))
                                                .getFirst()
                                                .id;
                                return bookings.book("patient@medislot.test", slot).id;
                            });
        } finally {
            TenantContext.clear();
        }
    }

    Connection connection() throws SQLException {
        return DriverManager.getConnection(
                DB.getJdbcUrl(), "medislot_app", "medislot-app-local-only");
    }

    HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    HttpResponse<String> call(
            HttpClient client, String method, String path, String clinic, String body)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path));
        if (clinic != null) builder.header("X-Clinic", clinic);
        if (!method.equals("GET")) {
            var csrf = json(call(client, "GET", "/csrf", null, null));
            builder.header(csrf.get("headerName").asString(), csrf.get("token").asString());
            builder.header(
                    "Content-Type",
                    path.equals("/login")
                            ? "application/x-www-form-urlencoded"
                            : "application/json");
        }
        return client.send(
                builder.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    HttpClient login(String email) throws Exception {
        var c = client();
        assertThat(
                        call(
                                        c,
                                        "POST",
                                        "/login",
                                        null,
                                        "username="
                                                + URLEncoder.encode(
                                                        email,
                                                        java.nio.charset.StandardCharsets.UTF_8)
                                                + "&password=MediSlot-demo-2026%21")
                                .statusCode())
                .isEqualTo(204);
        return c;
    }

    void setClinic(Connection connection, String value) throws SQLException {
        try (var query = connection.prepareStatement("select set_config('app.clinic_id',?,true)")) {
            query.setString(1, value);
            query.execute();
        }
    }
}
