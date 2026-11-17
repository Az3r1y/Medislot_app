package fr.medislot.tenant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

class TenantFilterTest {
    ClinicDirectory directory = mock(ClinicDirectory.class);
    TenantFilter filter = new TenantFilter(directory);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/professionals");
    MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setup() {
        request.setServletPath("/api/professionals");
        when(directory.findBySlug("cabinet-a"))
                .thenReturn(
                        Optional.of(
                                new ClinicDirectory.Clinic(
                                        1, "cabinet-a", "A", "Paris", "#176b5b")));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void missingClinicIs400() throws Exception {
        filter.doFilter(request, response, (a, b) -> fail("Ne doit pas continuer"));
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void invalidOrConflictingClinicIs400() throws Exception {
        request.addHeader("X-Clinic", "cabinet-a");
        request.setParameter("clinic", "cabinet-b");
        filter.doFilter(request, response, (a, b) -> fail("Ne doit pas continuer"));
        assertThat(response.getStatus()).isEqualTo(400);
        var invalid = new MockHttpServletRequest("GET", "/api/professionals");
        invalid.setServletPath("/api/professionals");
        invalid.addHeader("X-Clinic", "../cabinet-a");
        var result = new MockHttpServletResponse();
        filter.doFilter(invalid, result, (a, b) -> fail("Ne doit pas continuer"));
        assertThat(result.getStatus()).isEqualTo(400);
    }

    @Test
    void platformAccountIsRefusedOnTheCabinetApi() throws Exception {
        request.addHeader("X-Clinic", "cabinet-a");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                "master@medislot.test",
                                null,
                                java.util.List.of(
                                        new org.springframework.security.core.authority
                                                .SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
        filter.doFilter(request, response, (a, b) -> fail("Ne doit pas continuer"));
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void platformRoutesHaveNoClinic() throws Exception {
        var platform = new MockHttpServletRequest("GET", "/api/platform/clinics");
        platform.setServletPath("/api/platform/clinics");
        var passed = new boolean[] {false};
        filter.doFilter(platform, new MockHttpServletResponse(), (a, b) -> passed[0] = true);
        assertThat(passed[0]).isTrue();
    }

    @Test
    void unknownClinicIs404() throws Exception {
        request.addHeader("X-Clinic", "inconnu");
        filter.doFilter(request, response, (a, b) -> fail("Ne doit pas continuer"));
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void anonymousCanBrowseUsingTheParameter() throws Exception {
        request.setParameter("clinic", "cabinet-a");
        filter.doFilter(
                request, response, (a, b) -> assertThat(TenantContext.current()).isEqualTo(1L));
        assertThat(TenantContext.current()).isNull();
    }

    @Test
    void authenticatedMemberCanContinue() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                "member@test", "", java.util.List.of()));
        when(directory.member("member@test", 1)).thenReturn(true);
        request.addHeader("X-Clinic", "cabinet-a");
        filter.doFilter(
                request, response, (a, b) -> assertThat(TenantContext.required()).isEqualTo(1L));
        assertThat(TenantContext.current()).isNull();
    }

    @Test
    void authenticatedNonMemberGets404() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                "outsider@test", "", java.util.List.of()));
        request.addHeader("X-Clinic", "cabinet-a");
        filter.doFilter(request, response, (a, b) -> fail("Ne doit pas continuer"));
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(TenantContext.current()).isNull();
    }

    @Test
    void exceptionAlwaysClearsContext() {
        request.addHeader("X-Clinic", "cabinet-a");
        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        request,
                                        response,
                                        (a, b) -> {
                                            throw new jakarta.servlet.ServletException("échec");
                                        }))
                .isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(TenantContext.current()).isNull();
    }
}
