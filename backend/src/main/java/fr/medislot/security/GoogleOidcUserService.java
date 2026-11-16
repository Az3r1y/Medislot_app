package fr.medislot.security;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/** Après validation du jeton OpenID par Spring Security, charge le compte MediSlot associé. */
@Component
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OidcUserService google = new OidcUserService();
    private final GoogleAccounts accounts;

    public GoogleOidcUserService(GoogleAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser g = google.loadUser(request);
        var u =
                accounts.resolve(
                        g.getSubject(), g.getEmail(), g.getEmailVerified(), g.getFullName());
        return new AccountOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_" + u.role),
                g.getIdToken(),
                g.getUserInfo(),
                u.email);
    }
}
