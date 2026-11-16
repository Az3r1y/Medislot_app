package fr.medislot.security;

import fr.medislot.repository.UserRepository;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(UserRepository users) {
        return email ->
                users.findByEmail(email.toLowerCase(java.util.Locale.ROOT))
                        .map(
                                u ->
                                        User.withUsername(u.email)
                                                .password(u.password)
                                                .roles(u.role)
                                                .build())
                        .orElseThrow(() -> new UsernameNotFoundException("Identité inconnue"));
    }

    // Charset explicite : sans lui, les accents sortaient en ISO-8859-1.
    static void problem(HttpServletResponse res, int status, String title) throws IOException {
        res.setStatus(status);
        res.setContentType("application/problem+json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"title\":\"" + title + "\",\"status\":" + status + "}");
    }

    @Bean
    SecurityFilterChain filter(
            HttpSecurity http, LoginAttempts attempts, fr.medislot.tenant.ClinicDirectory clinics)
            throws Exception {
        // Verrou par compte, avant la vérification du mot de passe.
        http.addFilterBefore(
                (req, res, chain) -> {
                    var r = (jakarta.servlet.http.HttpServletRequest) req;
                    if ("POST".equals(r.getMethod())
                            && "/api/login".equals(r.getServletPath())
                            && attempts.locked(r.getParameter("username"))) {
                        problem(
                                (HttpServletResponse) res,
                                429,
                                "Trop de tentatives pour ce compte. Réessayez dans 15 minutes.");
                        return;
                    }
                    chain.doFilter(req, res);
                },
                UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(
                new fr.medislot.tenant.TenantFilter(clinics),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        http.authorizeHttpRequests(
                        a ->
                                a.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                                        .permitAll()
                                        // Avant le permitAll de /api/ab/** : la première règle qui
                                        // correspond s'applique.
                                        .requestMatchers("/api/clinic/agenda")
                                        .hasAnyRole("SECRETARY", "ADMIN")
                                        .requestMatchers("/api/ab/metrics")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                "/api/csrf",
                                                "/api/calendar/feed.ics",
                                                "/api/clinics",
                                                "/api/register",
                                                "/api/login",
                                                "/api/professionals/**",
                                                "/api/events",
                                                "/api/ab/**",
                                                "/actuator/health")
                                        .permitAll()
                                        .requestMatchers("/api/**")
                                        .authenticated()
                                        .anyRequest()
                                        .permitAll())
                .requestCache(c -> c.disable())
                .formLogin(
                        f ->
                                f.loginProcessingUrl("/api/login")
                                        .successHandler(
                                                (req, res, auth) -> {
                                                    attempts.succeeded(
                                                            req.getParameter("username"));
                                                    res.setStatus(204);
                                                })
                                        .failureHandler(
                                                (req, res, ex) -> {
                                                    attempts.failed(req.getParameter("username"));
                                                    problem(res, 401, "Identifiants incorrects");
                                                }))
                .logout(
                        l ->
                                l.logoutUrl("/api/logout")
                                        .logoutSuccessHandler(
                                                (req, res, auth) -> res.setStatus(204)))
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        problem(res, 401, "Connexion requise"))
                                        .accessDeniedHandler(
                                                (req, res, ex) ->
                                                        problem(
                                                                res,
                                                                403,
                                                                "Action refusée ou jeton CSRF expiré")));
        return http.build();
    }
}
