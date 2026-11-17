package fr.medislot.platform;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;
import fr.medislot.security.LoginAttempts;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Porte d'entrée de la console master, séparée de /api/login (patients et cabinets). Les comptes
 * PLATFORM_ADMIN ne peuvent se connecter que par ici.
 */
@RestController
@RequestMapping("/api/platform")
public class PlatformAuthController {
    public static final String ROLE = "PLATFORM_ADMIN";
    static final Duration SESSION = Duration.ofMinutes(15);
    static final String TOO_MANY = "Trop de tentatives. Réessayez dans 15 minutes.";
    static final String WRONG = "Identifiants incorrects.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PlatformJournal journal;
    private final PlatformDb db;
    // Compteurs distincts de ceux des cabinets : 5 échecs par compte, 50 par adresse IP.
    private final LoginAttempts accounts = new LoginAttempts(), addresses = new LoginAttempts(50);
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
    // Empreinte factice : une adresse inconnue coûte le même calcul BCrypt qu'une adresse connue.
    private final String dummyHash;

    public PlatformAuthController(
            UserRepository users, PasswordEncoder encoder, PlatformJournal journal, PlatformDb db) {
        this.users = users;
        this.encoder = encoder;
        this.journal = journal;
        this.db = db;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public record Credentials(
            @NotBlank(message = "Saisissez votre adresse e-mail.") @Size(max = 254) String email,
            @NotBlank(message = "Saisissez votre mot de passe.") @Size(max = 72) String password) {}

    public record Identity(long id, String name, String email) {}

    @PostMapping("/login")
    public Identity login(
            @Valid @RequestBody Credentials input,
            HttpServletRequest request,
            HttpServletResponse response) {
        String email = input.email().strip().toLowerCase(Locale.ROOT);
        String address = "ip:" + request.getRemoteAddr();
        if (accounts.locked(email) || addresses.locked(address))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY);
        Optional<AppUser> user =
                users.findByEmail(email).filter(u -> ROLE.equals(u.role) && u.password != null);
        boolean valid =
                encoder.matches(input.password(), user.map(u -> u.password).orElse(dummyHash));
        if (user.isEmpty() || !valid) {
            accounts.failed(email);
            addresses.failed(address);
            // Même message que le compte existe ou non.
            if (accounts.locked(email) || addresses.locked(address))
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, WRONG);
        }
        accounts.succeeded(email);
        addresses.succeeded(address);
        AppUser master = user.get();
        // Identifiant de session régénéré (fixation de session), session courte.
        request.getSession(true);
        request.changeSessionId();
        request.getSession().setMaxInactiveInterval((int) SESSION.toSeconds());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        master.email, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        db.global(
                () -> {
                    journal.record(master.email, PlatformJournal.LOGIN, null, null, null);
                    return null;
                });
        return new Identity(master.id, master.name, master.email);
    }

    @GetMapping("/me")
    public Identity me(Principal principal) {
        AppUser master = users.findByEmail(principal.getName()).orElseThrow();
        return new Identity(master.id, master.name, master.email);
    }
}
