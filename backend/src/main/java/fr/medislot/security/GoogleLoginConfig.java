package fr.medislot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Connexion avec Google, active seulement si GOOGLE_CLIENT_ID est renseigné : sans identifiants,
 * l'application démarre normalement et le bouton Google n'est pas proposé.
 */
@Configuration
@ConditionalOnExpression("!'${medislot.google.client-id:}'.isBlank()")
public class GoogleLoginConfig {
    // sous /api : seul ce préfixe est transmis au back par Nginx et par Vite
    public static final String AUTHORIZATION_BASE_URI = "/api/oauth2/authorization";
    public static final String CALLBACK_BASE_URI = "/api/login/oauth2/code";
    static final String DEFAULT_REDIRECT_URI =
            "{baseUrl}" + CALLBACK_BASE_URI + "/{registrationId}";

    @Bean
    ClientRegistrationRepository googleClientRegistration(
            @Value("${medislot.google.client-id}") String clientId,
            @Value("${medislot.google.client-secret:}") String clientSecret,
            @Value("${medislot.google.redirect-uri:}") String redirectUri) {
        if (clientSecret.isBlank())
            throw new IllegalStateException(
                    "GOOGLE_CLIENT_SECRET est obligatoire quand GOOGLE_CLIENT_ID est défini.");
        return new InMemoryClientRegistrationRepository(
                CommonOAuth2Provider.GOOGLE
                        .getBuilder("google")
                        .clientId(clientId.strip())
                        .clientSecret(clientSecret.strip())
                        .redirectUri(
                                redirectUri.isBlank() ? DEFAULT_REDIRECT_URI : redirectUri.strip())
                        .scope("openid", "email", "profile")
                        .build());
    }
}
