package fr.medislot.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

// Construit dans SecurityConfig uniquement : après restauration de la session, avant
// l'autorisation.
public class TenantFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Clinic";
    public static final String PARAM =
            "clinic"; // EventSource ne permet pas d'en-tête personnalisé.
    private static final Set<String> GLOBAL =
            Set.of(
                    "/api/csrf",
                    "/api/login",
                    "/api/logout",
                    "/api/me",
                    "/api/clinics",
                    "/api/auth/providers");
    private final ClinicDirectory clinics;

    public TenantFilter(ClinicDirectory clinics) {
        this.clinics = clinics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/")
                || request.getServletPath().startsWith("/api/platform/")
                || GLOBAL.contains(request.getServletPath());
    }

    static boolean platform(Authentication auth) {
        return auth != null
                && auth.isAuthenticated()
                && auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
    }

    public boolean allowed(Authentication auth, long clinic) {
        return auth == null
                || auth instanceof AnonymousAuthenticationToken
                || (auth.isAuthenticated() && clinics.member(auth.getName(), clinic));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TenantContext.clear();
        try {
            // Les deux mondes ne se mélangent pas : un compte plateforme n'appelle pas l'API des
            // cabinets.
            if (platform(SecurityContextHolder.getContext().getAuthentication())) {
                problem(response, 403, "Cette zone est réservée aux cabinets.");
                return;
            }
            String header = request.getHeader(HEADER), parameter = request.getParameter(PARAM);
            String slug = header != null ? header : parameter;
            if (slug == null
                    || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                    || slug.length() > 63
                    || (header != null && parameter != null && !header.equals(parameter))) {
                problem(response, 400, "Cabinet manquant ou invalide.");
                return;
            }
            var clinic = clinics.findBySlug(slug);
            if (clinic.isEmpty()
                    || !allowed(
                            SecurityContextHolder.getContext().getAuthentication(),
                            clinic.get().id())) {
                problem(response, 404, "Cabinet introuvable.");
                return;
            }
            TenantContext.set(clinic.get().id());
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static void problem(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"title\":\"" + message + "\"}");
    }
}
