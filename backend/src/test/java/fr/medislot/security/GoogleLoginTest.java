package fr.medislot.security;

import static org.junit.jupiter.api.Assertions.*;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;

// Flux de connexion Google jusqu'à la redirection vers Google ; l'échange de code n'est pas joué.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GoogleLoginTest {
    static final String REDIRECT = "http://localhost:8088/api/login/oauth2/code/google";

    @Container
    static PostgreSQLContainer db =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:18@sha256:2ea8630df176ad090ec92136dee850397c5af8781e4bcf2e4162593691c61bbc")
                            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", db::getJdbcUrl);
        r.add("spring.flyway.url", db::getJdbcUrl);
        r.add("spring.flyway.user", db::getUsername);
        r.add("spring.flyway.password", db::getPassword);
        r.add("spring.datasource.username", () -> "medislot_app");
        r.add("spring.datasource.password", () -> "medislot-app-local-only");
        r.add("medislot.google.client-id", () -> "test-client.apps.googleusercontent.com");
        r.add("medislot.google.client-secret", () -> "test-secret");
        r.add("medislot.google.redirect-uri", () -> REDIRECT);
    }

    @LocalServerPort int port;
    @Autowired GoogleAccounts accounts;
    @Autowired UserRepository users;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    final HttpClient client =
            HttpClient.newBuilder()
                    .cookieHandler(new CookieManager())
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @org.junit.jupiter.api.BeforeEach
    void clinic() {
        fr.medislot.tenant.TenantContext.set(1L);
    }

    @org.junit.jupiter.api.AfterEach
    void clearClinic() {
        fr.medislot.tenant.TenantContext.clear();
    }

    @Test
    void providersAnnounceGoogle() throws Exception {
        var r = get("/api/auth/providers");
        assertEquals(200, r.statusCode());
        assertEquals("{\"google\":true}", r.body());
    }

    @Test
    void authorizationRedirectsToGoogleWithStateNonceAndPkce() throws Exception {
        var r = get("/api/oauth2/authorization/google?clinic=cabinet-b");
        assertEquals(302, r.statusCode());
        String location = r.headers().firstValue("Location").orElseThrow();
        assertTrue(location.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"), location);
        String query = URLDecoder.decode(location, StandardCharsets.UTF_8);
        assertTrue(query.contains("client_id=test-client.apps.googleusercontent.com"), query);
        assertTrue(query.contains("redirect_uri=" + REDIRECT), query);
        assertTrue(query.contains("scope=openid email profile"), query);
        assertTrue(query.contains("state="), query);
        assertTrue(query.contains("nonce="), query);
        assertTrue(query.contains("code_challenge_method=S256"), query);
        assertFalse(query.contains("test-secret"), "le secret ne part jamais vers le navigateur");
    }

    // Rappel sans demande en cours (state inconnu) : refus, retour à l'accueil sans session.
    @Test
    void forgedCallbackIsRejected() throws Exception {
        var r = get("/api/login/oauth2/code/google?code=forged&state=forged");
        assertEquals(302, r.statusCode());
        assertEquals("/?google_error=failed", r.headers().firstValue("Location").orElseThrow());
        assertEquals(401, get("/api/me").statusCode());
    }

    // L'utilisateur refuse sur l'écran Google : Google rappelle avec error=access_denied.
    @Test
    void userCancellationIsReported() throws Exception {
        String location =
                get("/api/oauth2/authorization/google?clinic=cabinet-b")
                        .headers()
                        .firstValue("Location")
                        .get();
        String state = location.replaceAll(".*[?&]state=([^&]+).*", "$1");
        var r = get("/api/login/oauth2/code/google?error=access_denied&state=" + state);
        assertEquals(302, r.statusCode());
        assertEquals(
                "/c/cabinet-b/?google_error=access_denied",
                r.headers().firstValue("Location").orElseThrow());
        assertEquals(401, get("/api/me").statusCode());
    }

    @Test
    void googleAccountIsCreatedOnceThenFoundBySubject() {
        var created = accounts.resolve("it-sub-1", "Google.User@Exemple.fr", true, "Google User");
        assertNotNull(created.id);
        assertNull(created.password);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from membership where user_id=? and clinic_id=1",
                        Integer.class,
                        created.id));
        var again = accounts.resolve("it-sub-1", "autre-adresse@exemple.fr", true, "Renamed");
        assertEquals(created.id, again.id);
        assertEquals("google.user@exemple.fr", again.email);
    }

    // La contrainte V10 interdit un compte sans aucun moyen de connexion.
    @Test
    void accountWithoutAnyCredentialIsRejectedByDatabase() {
        var u = new AppUser();
        u.email = "sans-identifiant@exemple.fr";
        u.name = "X";
        u.role = "PATIENT";
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> users.saveAndFlush(u));
    }

    // Un compte Google n'a pas de mot de passe : le formulaire répond comme pour un inconnu.
    @Test
    void googleAccountCannotUsePasswordLogin() throws Exception {
        accounts.resolve("it-sub-2", "sans-mot-de-passe@exemple.fr", true, "G");
        String csrf = get("/api/csrf").body();
        String header = csrf.replaceAll(".*\"headerName\":\"([^\"]+)\".*", "$1"),
                token = csrf.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        var login =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/login"))
                        .header(header, token)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        "username=sans-mot-de-passe%40exemple.fr&password=x"))
                        .build();
        assertEquals(401, client.send(login, HttpResponse.BodyHandlers.discarding()).statusCode());
    }

    @Test
    void missingAndUnknownClinicAreRejected() throws Exception {
        assertEquals(400, get("/api/oauth2/authorization/google").statusCode());
        assertEquals(404, get("/api/oauth2/authorization/google?clinic=inconnu").statusCode());
    }
}
