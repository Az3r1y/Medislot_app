package fr.medislot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MedislotApplication {
    public static void main(String[] args) {
        // Vérifié avant Spring : Flyway se connecte dès le démarrage.
        if (blank(System.getenv("DB_PASSWORD"))
                && blank(System.getProperty("spring.datasource.password"))) {
            System.err.println(
                    "DB_PASSWORD est obligatoire : aucun mot de passe de base par défaut. Démarrage refusé.");
            System.exit(1);
        }
        SpringApplication.run(MedislotApplication.class, args);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
