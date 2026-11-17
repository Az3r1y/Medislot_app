package fr.medislot.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pseudonyme d'un patient vu par la console master : empreinte HMAC-SHA256 avec une clé secrète,
 * stable pour un même patient dans un même cabinet, différente d'un cabinet à l'autre. Le nom et
 * l'identifiant du patient ne quittent jamais le back.
 */
@Component
public class Pseudonyms {
    private static final Logger LOG = LoggerFactory.getLogger(Pseudonyms.class);
    private final byte[] key;

    public Pseudonyms(@Value("${medislot.platform.pseudonym-key:}") String configured) {
        if (configured == null || configured.isBlank()) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
            LOG.warn(
                    "PSEUDONYM_KEY absente : clé aléatoire, les pseudonymes changeront au prochain démarrage.");
        } else {
            key = configured.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String of(long clinic, long patient) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((clinic + ":" + patient).getBytes(StandardCharsets.UTF_8));
            return "P-" + HexFormat.of().withUpperCase().formatHex(digest, 0, 3);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible.", e);
        }
    }
}
