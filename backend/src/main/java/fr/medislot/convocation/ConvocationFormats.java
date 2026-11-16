package fr.medislot.convocation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

// Toujours l'heure de Paris, quel que soit le fuseau du serveur.
final class ConvocationFormats {
    static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE).withZone(PARIS);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("H' h 'mm", Locale.FRANCE).withZone(PARIS);
    // mêmes libellés que le front (types.ts)
    private static final Map<String, String> SPECIALTIES =
            Map.of(
                    "general",
                    "Médecine générale",
                    "cardiology",
                    "Cardiologie",
                    "dermatology",
                    "Dermatologie",
                    "dentistry",
                    "Chirurgie dentaire");

    private ConvocationFormats() {}

    static String day(Instant i) {
        return DAY.format(i);
    }

    static String time(Instant i) {
        return TIME.format(i);
    }

    static String specialty(String code) {
        return SPECIALTIES.getOrDefault(code, code);
    }
}
