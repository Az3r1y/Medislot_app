package fr.medislot.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Optional;

class GoogleAccountsTest {
    final UserRepository users = mock(UserRepository.class);
    final GoogleAccounts accounts = new GoogleAccounts(users);

    static String code(OAuth2AuthenticationException e) {
        return e.getError().getErrorCode();
    }

    @Test
    void linkedAccountIsFoundBySubjectEvenIfEmailChanged() {
        var u = new AppUser();
        u.email = "ancienne@exemple.fr";
        when(users.findByGoogleSubject("sub-1")).thenReturn(Optional.of(u));
        assertSame(u, accounts.resolve("sub-1", "nouvelle@exemple.fr", true, "Camille"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void firstLoginCreatesPatientWithoutPassword() {
        when(users.findByGoogleSubject("sub-2")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var u = accounts.resolve("sub-2", "  Camille@Exemple.FR ", true, " Camille Dupont ");
        assertEquals("camille@exemple.fr", u.email);
        assertEquals("Camille Dupont", u.name);
        assertEquals("PATIENT", u.role);
        assertEquals("sub-2", u.googleSubject);
        assertNull(u.password);
    }

    @Test
    void unverifiedEmailIsRefused() {
        when(users.findByGoogleSubject("sub-3")).thenReturn(Optional.empty());
        for (Boolean verified : new Boolean[] {false, null})
            assertEquals(
                    GoogleAccounts.EMAIL_NOT_VERIFIED,
                    code(
                            assertThrows(
                                    OAuth2AuthenticationException.class,
                                    () -> accounts.resolve("sub-3", "a@b.fr", verified, "A"))));
        verify(users, never()).saveAndFlush(any());
    }

    // Pas de liaison automatique avec un compte local : son créateur n'a jamais prouvé l'adresse.
    @Test
    void existingLocalAccountIsNeverLinkedAutomatically() {
        when(users.findByGoogleSubject("sub-4")).thenReturn(Optional.empty());
        when(users.existsByEmail("patient@medislot.test")).thenReturn(true);
        assertEquals(
                GoogleAccounts.ACCOUNT_EXISTS,
                code(
                        assertThrows(
                                OAuth2AuthenticationException.class,
                                () ->
                                        accounts.resolve(
                                                "sub-4", "Patient@medislot.test", true, "X"))));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void concurrentCreationBecomesAuthenticationError() {
        when(users.findByGoogleSubject("sub-5")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));
        assertEquals(
                GoogleAccounts.ACCOUNT_EXISTS,
                code(
                        assertThrows(
                                OAuth2AuthenticationException.class,
                                () -> accounts.resolve("sub-5", "a@b.fr", true, "A"))));
    }

    @Test
    void missingSubjectIsRefused() {
        assertEquals(
                GoogleAccounts.INVALID_PROFILE,
                code(
                        assertThrows(
                                OAuth2AuthenticationException.class,
                                () -> accounts.resolve(" ", "a@b.fr", true, "A"))));
    }

    @Test
    void displayNameFallsBackAndIsBounded() {
        assertEquals("camille", GoogleAccounts.displayName(null, "camille@exemple.fr"));
        assertEquals("camille", GoogleAccounts.displayName("  ", "camille@exemple.fr"));
        assertEquals(120, GoogleAccounts.displayName("x".repeat(300), "a@b.fr").length());
    }
}
