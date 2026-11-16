package fr.medislot.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClinicDirectory {
    public record Clinic(long id, String slug, String name, String city, String color) {}

    private final JdbcTemplate jdbc;

    public ClinicDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Clinic> findBySlug(String slug) {
        return jdbc
                .query(
                        "select id, slug, name, city, color from clinic where slug=?",
                        (rs, row) ->
                                new Clinic(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getString(4),
                                        rs.getString(5)),
                        slug)
                .stream()
                .findFirst();
    }

    public boolean member(String email, long clinic) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
            select exists(select 1 from membership m join app_user u on u.id=m.user_id
            where u.email=? and m.clinic_id=?)
            """,
                        Boolean.class,
                        email,
                        clinic));
    }

    public List<Clinic> list(String email) {
        return jdbc.query(
                """
            select c.id,c.slug,c.name,c.city,c.color from clinic c
            where (cast(? as text) is null or exists(select 1 from membership m join app_user u on u.id=m.user_id
            where m.clinic_id=c.id and u.email=?)) order by c.id
            """,
                (rs, row) ->
                        new Clinic(
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getString(5)),
                email,
                email);
    }
}
