package fr.medislot.geo;

import fr.medislot.repository.ProfessionalRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "medislot.geo.finder", havingValue = "box", matchIfMissing = true)
public class BoxNearbyFinder implements NearbyFinder {
    private final ProfessionalRepository professionals;

    public BoxNearbyFinder(ProfessionalRepository professionals) {
        this.professionals = professionals;
    }

    @Override
    public List<Found> find(NearbyQuery q) {
        var box = BoundingBox.around(q.lat(), q.lon(), q.radiusKm());
        return professionals
                .findInBox(box.minLat(), box.maxLat(), box.minLon(), box.maxLon(), q.specialty())
                .stream()
                .map(
                        p ->
                                new Found(
                                        p,
                                        GeoDistance.haversineKm(
                                                q.lat(), q.lon(), p.latitude, p.longitude)))
                // Les coins de la boîte sont hors du cercle.
                .filter(f -> f.distanceKm() <= q.radiusKm())
                .toList();
    }
}
