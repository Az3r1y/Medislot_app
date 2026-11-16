package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.*;
import java.net.http.*;

// /api/ab/metrics est réservé aux administrateurs ; le reste de /api/ab/** reste public.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AbMetricsSecurityTest {
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
        r.add("AB_ENABLED", () -> "true");
    }

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    int get(HttpClient c, String path) throws Exception {
        return c.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                                .build(),
                        HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    HttpClient loggedIn(String email) throws Exception {
        var c = client();
        String csrf =
                c.send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:" + port + "/api/csrf"))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .body();
        String header = csrf.replaceAll(".*\"headerName\":\"([^\"]+)\".*", "$1"),
                token = csrf.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        var login =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/login"))
                        .header(header, token)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        "username="
                                                + URLEncoder.encode(email, "UTF-8")
                                                + "&password="
                                                + URLEncoder.encode(
                                                        "MediSlot-demo-2026!", "UTF-8")))
                        .build();
        assertEquals(
                204,
                c.send(login, HttpResponse.BodyHandlers.discarding()).statusCode(),
                "connexion " + email);
        return c;
    }

    @Test
    void anonymousGets401() throws Exception {
        assertEquals(401, get(client(), "/api/ab/metrics"));
    }

    @Test
    void patientGets403() throws Exception {
        assertEquals(403, get(loggedIn("patient@medislot.test"), "/api/ab/metrics"));
    }

    @Test
    void adminGets200() throws Exception {
        if (!users.existsByEmail("admin@medislot.test")) {
            var u = new AppUser();
            u.email = "admin@medislot.test";
            u.name = "Admin";
            u.role = "ADMIN";
            u.password = encoder.encode("MediSlot-demo-2026!");
            users.save(u);
        }
        assertEquals(200, get(loggedIn("admin@medislot.test"), "/api/ab/metrics"));
    }

    @Test
    void restOfAbStaysPublic() throws Exception {
        assertEquals(200, get(client(), "/api/ab/assignment"));
    }

    // Même le bon mot de passe est refusé ; les autres comptes ne sont pas touchés.
    @Test
    void accountLockedAfterFiveFailures() throws Exception {
        var c = client();
        String csrf =
                c.send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:" + port + "/api/csrf"))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .body();
        String header = csrf.replaceAll(".*\"headerName\":\"([^\"]+)\".*", "$1"),
                token = csrf.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        java.util.function.Function<String, HttpResponse<String>> login =
                pw -> {
                    try {
                        return c.send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:" + port + "/api/login"))
                                        .header(header, token)
                                        .header("Content-Type", "application/x-www-form-urlencoded")
                                        .POST(
                                                HttpRequest.BodyPublishers.ofString(
                                                        "username=autre%40medislot.test&password="
                                                                + URLEncoder.encode(pw, "UTF-8")))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        for (int i = 0; i < 5; i++) {
            var r = login.apply("wrong-" + i);
            assertEquals(401, r.statusCode());
            assertEquals(
                    "application/problem+json;charset=UTF-8",
                    r.headers().firstValue("Content-Type").orElse(""));
        }
        var locked = login.apply("MediSlot-demo-2026!");
        assertEquals(429, locked.statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(
                locked.body().contains("Réessayez dans 15 minutes"), locked.body());
        assertEquals(
                403,
                get(loggedIn("patient@medislot.test"), "/api/ab/metrics"),
                "un autre compte se connecte normalement");
    }
}
