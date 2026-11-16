package fr.medislot.security;

import fr.medislot.tenant.ClinicDirectory;
import fr.medislot.tenant.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Le cabinet voyage dans la demande OAuth sauvegardée en session, liée au state. */
final class GoogleClinicFlow extends OncePerRequestFilter
        implements OAuth2AuthorizationRequestResolver {
    private static final String CLINIC = "medislot.google.clinic";
    private static final String CLINIC_ID = "medislot.google.clinicId";
    private static final String RETURN_CLINIC = "medislot.google.returnClinic";
    private final ClinicDirectory clinics;
    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final HttpSessionOAuth2AuthorizationRequestRepository repository =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    GoogleClinicFlow(ClientRegistrationRepository registrations, ClinicDirectory clinics) {
        this.clinics = clinics;
        delegate =
                new DefaultOAuth2AuthorizationRequestResolver(
                        registrations, GoogleLoginConfig.AUTHORIZATION_BASE_URI);
    }

    HttpSessionOAuth2AuthorizationRequestRepository repository() {
        return repository;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return bind(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return bind(request, delegate.resolve(request, registrationId));
    }

    private OAuth2AuthorizationRequest bind(
            HttpServletRequest request, OAuth2AuthorizationRequest authorization) {
        if (authorization == null) return null;
        String slug = request.getParameter("clinic");
        if (!validSlug(slug)) throw invalid();
        var clinic = clinics.findBySlug(slug).orElseThrow(GoogleClinicFlow::invalid);
        return OAuth2AuthorizationRequest.from(authorization)
                .attributes(
                        attributes -> {
                            attributes.put(CLINIC, clinic.slug());
                            attributes.put(CLINIC_ID, clinic.id());
                        })
                .build();
    }

    private static OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_request"));
    }

    private static boolean validSlug(String value) {
        return value != null && value.length() <= 63 && value.matches("[a-z0-9]+(?:-[a-z0-9]+)*");
    }

    private boolean start(HttpServletRequest request) {
        return (GoogleLoginConfig.AUTHORIZATION_BASE_URI + "/google")
                .equals(request.getServletPath());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !start(request)
                && !request.getServletPath().startsWith(GoogleLoginConfig.CALLBACK_BASE_URI + "/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TenantContext.clear();
        try {
            if (start(request)) {
                String slug = request.getParameter("clinic");
                if (!validSlug(slug)) {
                    problem(response, 400, "Cabinet manquant ou invalide.");
                    return;
                }
                if (clinics.findBySlug(slug).isEmpty()) {
                    problem(response, 404, "Cabinet introuvable.");
                    return;
                }
            } else {
                var authorization = repository.loadAuthorizationRequest(request);
                // loadAuthorizationRequest vérifie que le state reçu correspond à celui de la
                // session.
                if (authorization != null) {
                    String slug = authorization.getAttribute(CLINIC);
                    Long id = authorization.getAttribute(CLINIC_ID);
                    if (!validSlug(slug) || id == null) {
                        SecurityConfig.redirect(response, "/?google_error=failed");
                        return;
                    }
                    request.setAttribute(RETURN_CLINIC, slug);
                    TenantContext.set(id);
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    static String returnPath(HttpServletRequest request) {
        Object slug = request.getAttribute(RETURN_CLINIC);
        return slug instanceof String value && validSlug(value) ? "/c/" + value + "/" : "/";
    }

    private static void problem(HttpServletResponse response, int status, String title)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"title\":\"" + title + "\"}");
    }
}
