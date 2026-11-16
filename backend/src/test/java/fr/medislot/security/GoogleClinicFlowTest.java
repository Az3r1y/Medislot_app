package fr.medislot.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import fr.medislot.tenant.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Optional;

class GoogleClinicFlowTest {
    final ClinicDirectory clinics = mock(ClinicDirectory.class);
    final GoogleClinicFlow flow;

    GoogleClinicFlowTest() {
        var registration =
                CommonOAuth2Provider.GOOGLE
                        .getBuilder("google")
                        .clientId("test")
                        .clientSecret("test")
                        .build();
        flow =
                new GoogleClinicFlow(
                        new InMemoryClientRegistrationRepository(registration), clinics);
        when(clinics.findBySlug("cabinet-b"))
                .thenReturn(
                        Optional.of(
                                new ClinicDirectory.Clinic(
                                        2L, "cabinet-b", "B", "Lyon", "#3955a5")));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }

    MockHttpServletRequest callback(String state) {
        var start = request("/api/oauth2/authorization/google");
        start.setParameter("clinic", "cabinet-b");
        OAuth2AuthorizationRequest authorization = flow.resolve(start);
        flow.repository()
                .saveAuthorizationRequest(authorization, start, new MockHttpServletResponse());
        var callback = request("/api/login/oauth2/code/google");
        callback.setSession(start.getSession(false));
        callback.setParameter("state", state == null ? authorization.getState() : state);
        // Le callback ne doit pas pouvoir remplacer le cabinet associé au state.
        callback.setParameter("clinic", "cabinet-a");
        return callback;
    }

    @Test
    void validStateRestoresOriginalClinicAndReturnPath() throws Exception {
        var callback = callback(null);
        flow.doFilter(
                callback,
                new MockHttpServletResponse(),
                (req, res) -> {
                    assertThat(TenantContext.current()).isEqualTo(2L);
                    assertThat(GoogleClinicFlow.returnPath(callback)).isEqualTo("/c/cabinet-b/");
                });
        assertThat(TenantContext.current()).isNull();
    }

    @Test
    void wrongStateDoesNotSetAClinic() throws Exception {
        var callback = callback("forged");
        flow.doFilter(
                callback,
                new MockHttpServletResponse(),
                (req, res) -> assertThat(TenantContext.current()).isNull());
        assertThat(GoogleClinicFlow.returnPath(callback)).isEqualTo("/");
    }

    @Test
    void downstreamFailureStillClearsTheContext() {
        var callback = callback(null);
        assertThatThrownBy(
                        () ->
                                flow.doFilter(
                                        callback,
                                        new MockHttpServletResponse(),
                                        (req, res) -> {
                                            throw new jakarta.servlet.ServletException("failure");
                                        }))
                .isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(TenantContext.current()).isNull();
    }

    @Test
    void absentClinicIsRejectedBeforeRedirecting() throws Exception {
        var response = new MockHttpServletResponse();
        flow.doFilter(
                request("/api/oauth2/authorization/google"),
                response,
                (req, res) -> fail("Ne doit pas continuer"));
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void unknownClinicIs404() throws Exception {
        var start = request("/api/oauth2/authorization/google");
        start.setParameter("clinic", "inconnu");
        var response = new MockHttpServletResponse();
        flow.doFilter(start, response, (req, res) -> fail("Ne doit pas continuer"));
        assertThat(response.getStatus()).isEqualTo(404);
    }
}
