package fr.medislot.security;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Relie une identité Google à un compte MediSlot.
 *
 * <p>Le compte est retrouvé par l'identifiant « sub » de Google, stable même si l'adresse change. À
 * la première connexion, un compte patient est créé si l'adresse est vérifiée par Google. Un compte
 * local existant avec la même adresse n'est jamais relié automatiquement : l'inscription locale ne
 * vérifie pas l'adresse, et une liaison automatique permettrait à celui qui a créé ce compte de
 * lire les rendez-vous du vrai titulaire de l'adresse.
 */
@Service
public class GoogleAccounts {
    // codes renvoyés au navigateur après un échec (voir SecurityConfig)
    public static final String EMAIL_NOT_VERIFIED = "email_not_verified";
    public static final String ACCOUNT_EXISTS = "account_exists";
    public static final String INVALID_PROFILE = "invalid_profile";

    private final UserRepository users;

    public GoogleAccounts(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public AppUser resolve(String subject, String email, Boolean emailVerified, String name) {
        if (subject == null || subject.isBlank() || subject.length() > 255)
            throw refused(INVALID_PROFILE);
        var linked = users.findByGoogleSubject(subject);
        if (linked.isPresent()) return linked.get();
        if (!Boolean.TRUE.equals(emailVerified) || email == null || email.isBlank())
            throw refused(EMAIL_NOT_VERIFIED);
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254) throw refused(INVALID_PROFILE);
        if (users.existsByEmail(normalized)) throw refused(ACCOUNT_EXISTS);
        AppUser u = new AppUser();
        u.email = normalized;
        u.name = displayName(name, normalized);
        u.role = "PATIENT";
        u.googleSubject = subject;
        try {
            return users.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            // deux premières connexions simultanées, ou adresse prise entre-temps
            throw refused(ACCOUNT_EXISTS);
        }
    }

    static String displayName(String name, String email) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) {
            int at = email.indexOf('@');
            n = at > 0 ? email.substring(0, at) : "Patient";
        }
        return n.length() > 120 ? n.substring(0, 120) : n;
    }

    private static OAuth2AuthenticationException refused(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }
}
