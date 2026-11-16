package fr.medislot.geo;

import fr.medislot.repository.ProfessionalRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Variante PostGIS : ST_DWithin sur une colonne geography indexée. Les distances sont calculées sur
 * l'ellipsoïde, à quelques mètres de la haversine.
 */
@Component
@ConditionalOnProperty(name = "medislot.geo.finder", havingValue = "postgis")
public class PostgisNearbyFinder implements NearbyFinder {
    static final String SQL =
            """
  SELECT id, ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 AS km
  FROM professional
  WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ? * 1000)
    AND (? = '' OR specialty = ?)
  """;
    private final JdbcTemplate jdbc;
    private final ProfessionalRepository professionals;

    public PostgisNearbyFinder(JdbcTemplate jdbc, ProfessionalRepository professionals) {
        this.jdbc = jdbc;
        this.professionals = professionals;
    }

    @Override
    public List<Found> find(NearbyQuery q) {
        // ST_MakePoint attend (longitude, latitude).
        Map<Long, Double> km = new HashMap<>();
        jdbc.query(
                SQL,
                rs -> {
                    km.put(rs.getLong("id"), rs.getDouble("km"));
                },
                q.lon(),
                q.lat(),
                q.lon(),
                q.lat(),
                q.radiusKm(),
                q.specialty(),
                q.specialty());
        return professionals.findAllById(km.keySet()).stream()
                .map(p -> new Found(p, km.get(p.id)))
                .toList();
    }
}
