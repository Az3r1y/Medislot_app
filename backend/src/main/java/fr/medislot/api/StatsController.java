package fr.medislot.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StatsController {
    private final JdbcTemplate jdbc;

    public StatsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/clinic/stats")
    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        return Map.of(
                "patients",
                        jdbc.queryForObject(
                                """
                select count(*) from app_user u join membership m on m.user_id=u.id
                where u.role='PATIENT' and m.clinic_id=current_clinic()
                """,
                                Long.class),
                "appointments", jdbc.queryForObject("select count(*) from appointment", Long.class),
                "professionals",
                        jdbc.queryForObject("select count(*) from professional", Long.class));
    }
}
