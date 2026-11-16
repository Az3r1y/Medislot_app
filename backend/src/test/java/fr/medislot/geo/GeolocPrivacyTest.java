package fr.medislot.geo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.regex.*;

// La position passe dans la chaîne de requête : le journal de Nginx ne doit jamais la contenir.
class GeolocPrivacyTest {
    static String conf() throws Exception {
        for (String p : new String[] {"../frontend/nginx.conf", "frontend/nginx.conf"})
            if (Files.exists(Path.of(p))) return Files.readString(Path.of(p));
        throw new AssertionError("frontend/nginx.conf introuvable");
    }

    @Test
    void logFormatHasNoQueryString() throws Exception {
        Matcher m = Pattern.compile("log_format\\s+(\\w+)\\s+([^;]+);").matcher(conf());
        assertThat(m.find()).as("un log_format est déclaré").isTrue();
        assertThat(m.group(2))
                .doesNotContain(
                        "$request ",
                        "$request\"",
                        "$request'",
                        "$request_uri",
                        "$args",
                        "$query_string",
                        "$arg_");
        assertThat(m.group(2)).contains("$uri");
    }

    @Test
    void serverUsesOnlyThatFormat() throws Exception {
        String c = conf();
        Matcher name = Pattern.compile("log_format\\s+(\\w+)").matcher(c);
        name.find();
        Matcher logs = Pattern.compile("access_log\\s+([^;]+);").matcher(c);
        int n = 0;
        while (logs.find()) {
            n++;
            assertThat(logs.group(1))
                    .as("access_log " + logs.group(1))
                    .matches("(off|\\S+\\s+" + name.group(1) + ")");
        }
        assertThat(n).as("au moins un access_log, dans le serveur qui sert /api/").isPositive();
    }
}
