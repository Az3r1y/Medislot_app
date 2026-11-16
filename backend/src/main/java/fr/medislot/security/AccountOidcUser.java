package fr.medislot.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.io.Serial;
import java.util.Collection;

/**
 * Utilisateur connecté avec Google. getName() renvoie l'adresse du compte MediSlot, comme pour une
 * connexion par mot de passe : les contrôleurs identifient le patient par Principal.getName().
 * Sérialisable : la session est stockée en base (Spring Session JDBC).
 */
final class AccountOidcUser extends DefaultOidcUser {
    @Serial private static final long serialVersionUID = 1L;

    private final String accountEmail;

    AccountOidcUser(
            Collection<? extends GrantedAuthority> authorities,
            OidcIdToken idToken,
            OidcUserInfo userInfo,
            String accountEmail) {
        super(authorities, idToken, userInfo, IdTokenClaimNames.SUB);
        this.accountEmail = accountEmail;
    }

    @Override
    public String getName() {
        return accountEmail;
    }
}
