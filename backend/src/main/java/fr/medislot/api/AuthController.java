package fr.medislot.api;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final boolean googleEnabled;

    public AuthController(
            UserRepository users,
            PasswordEncoder encoder,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            org.springframework.beans.factory.ObjectProvider<
                            org.springframework.security.oauth2.client.registration
                                    .ClientRegistrationRepository>
                    google) {
        this.users = users;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.googleEnabled = google.getIfAvailable() != null;
    }

    public record Providers(boolean google) {}

    @GetMapping("/auth/providers")
    public Providers providers() {
        return new Providers(googleEnabled);
    }

    public record Identity(Long id, String name, String email, String role) {}

    // Messages affichés sous les champs du formulaire (RGAA 11.10).
    public record Registration(
            @Email(message = "Saisissez une adresse e-mail valide, par exemple nom@exemple.fr.")
                    @NotBlank(message = "Saisissez votre adresse e-mail.")
                    @Size(max = 254, message = "254 caractères au plus.")
                    String email,
            @NotBlank(message = "Saisissez un mot de passe.")
                    @Size(min = 12, max = 72, message = "12 caractères au moins, 72 au plus.")
                    String password,
            @NotBlank(message = "Saisissez votre nom.")
                    @Size(max = 120, message = "120 caractères au plus.")
                    String name,
            // Champ piège : invisible pour un humain, rempli par les robots.
            String website) {}

    @GetMapping("/csrf")
    public java.util.Map<String, String> csrf(CsrfToken token) {
        return java.util.Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/me")
    public Identity me(java.security.Principal principal) {
        AppUser u = users.findByEmail(principal.getName()).orElseThrow();
        return new Identity(u.id, u.name, u.email, u.role);
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody Registration input) {
        if (input.website() != null && !input.website().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requête refusée.");
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le mot de passe doit contenir au maximum 72 octets UTF-8.");
        String email = input.email().strip().toLowerCase(java.util.Locale.ROOT);
        // Adresse déjà inscrite : même réponse et même durée (hachage compris) qu'une création,
        // pour ne pas révéler qui a un compte. La connexion qui suit échoue normalement.
        String hash = encoder.encode(input.password());
        if (users.existsByEmail(email)) return;
        AppUser u = new AppUser();
        u.email = email;
        u.name = input.name().strip();
        u.password = hash;
        u.role = "PATIENT";
        users.saveAndFlush(u);
        jdbc.update(
                "insert into membership(user_id, clinic_id) values (?, ?)",
                u.id,
                fr.medislot.tenant.TenantContext.required());
    }
}
