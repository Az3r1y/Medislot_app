package fr.medislot.security;

import fr.medislot.repository.UserRepository;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
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
        // Compte Google sans mot de passe local : traité comme une identité inconnue (401).
        return email ->
                users.findByEmail(email.toLowerCase(java.util.Locale.ROOT))
                        .filter(u -> u.password != null)
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

    // Seuls les codes connus repartent vers le navigateur, les autres deviennent « failed ».
    static final java.util.Set<String> GOOGLE_ERRORS =
            java.util.Set.of(
                    GoogleAccounts.ACCOUNT_EXISTS,
                    GoogleAccounts.EMAIL_NOT_VERIFIED,
                    "access_denied");

    // Location relative : derrière Nginx ou Vite, le back ne connaît pas l'adresse publique.
    static void redirect(HttpServletResponse res, String location) {
        res.setStatus(HttpServletResponse.SC_FOUND);
        res.setHeader("Location", location);
    }

    @Bean
    SecurityFilterChain filter(
            HttpSecurity http,
            LoginAttempts attempts,
            ObjectProvider<ClientRegistrationRepository> google,
            GoogleOidcUserService googleUsers)
            throws Exception {
        if (google.getIfAvailable() != null)
            http.oauth2Login(
                    o ->
                            o.authorizationEndpoint(
                                            a ->
                                                    a.baseUri(
                                                            GoogleLoginConfig
                                                                    .AUTHORIZATION_BASE_URI))
                                    .redirectionEndpoint(
                                            r ->
                                                    r.baseUri(
                                                            GoogleLoginConfig.CALLBACK_BASE_URI
                                                                    + "/*"))
                                    .userInfoEndpoint(u -> u.oidcUserService(googleUsers))
                                    // jetons Google inutiles après la connexion : gardés dans la
                                    // session (effacés à la déconnexion), pas en mémoire du serveur
                                    .authorizedClientRepository(
                                            new HttpSessionOAuth2AuthorizedClientRepository())
                                    .successHandler((req, res, auth) -> redirect(res, "/"))
                                    .failureHandler(
                                            (req, res, ex) -> {
                                                String code =
                                                        ex
                                                                                instanceof
                                                                                OAuth2AuthenticationException
                                                                                        o2
                                                                        && GOOGLE_ERRORS.contains(
                                                                                o2.getError()
                                                                                        .getErrorCode())
                                                                ? o2.getError().getErrorCode()
                                                                : "failed";
                                                redirect(res, "/?google_error=" + code);
                                            }));
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
        http.authorizeHttpRequests(
                        a ->
                                a.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                                        .permitAll()
                                        // Avant le permitAll de /api/ab/** : la première règle qui
                                        // correspond s'applique.
                                        .requestMatchers("/api/ab/metrics")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                "/api/csrf",
                                                "/api/register",
                                                "/api/login",
                                                "/api/auth/providers",
                                                GoogleLoginConfig.AUTHORIZATION_BASE_URI + "/**",
                                                GoogleLoginConfig.CALLBACK_BASE_URI + "/**",
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
