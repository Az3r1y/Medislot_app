package fr.medislot.geo;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class NearbyService {
    // Ordre stable d'une page à l'autre, même à distance égale.
    static final Comparator<NearbyFinder.Found> ORDER =
            Comparator.comparingDouble(NearbyFinder.Found::distanceKm)
                    .thenComparing(f -> f.professional().name)
                    .thenComparing(f -> f.professional().id);
    private final NearbyFinder finder;

    public NearbyService(NearbyFinder finder) {
        this.finder = finder;
    }

    public record Page(List<NearbyFinder.Found> items, long total) {}

    public Page search(NearbyQuery q) {
        var sorted = finder.find(q).stream().sorted(ORDER).toList();
        // En long : page * size peut dépasser Integer.MAX_VALUE.
        int from = (int) Math.min((long) q.page() * q.size(), sorted.size()),
                to = (int) Math.min((long) from + q.size(), sorted.size());
        return new Page(sorted.subList(from, to), sorted.size());
    }
}
