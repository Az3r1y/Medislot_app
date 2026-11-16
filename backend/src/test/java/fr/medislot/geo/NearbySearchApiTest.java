package fr.medislot.geo;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fr.medislot.domain.Professional;
import fr.medislot.repository.ProfessionalRepository;

import org.junit.jupiter.api.Test;

import java.util.List;

// Le dépôt est simulé : il renvoie ce que la boîte englobante laisserait passer.
class NearbySearchApiTest {
    static Professional pro(long id, String name, double lat, double lon) {
        var p = new Professional();
        p.id = id;
        p.name = name;
        p.latitude = lat;
        p.longitude = lon;
        p.specialty = "general";
        return p;
    }

    static final Professional CHARITE = pro(3, "Dr Amel Benali", 45.7558, 4.8330),
            LAFAYETTE = pro(4, "Dr Hugo Petit", 45.7637, 4.8446),
            REPUBLIQUE = pro(7, "Dr Hélène Durand", 45.7651, 4.8358),
            CAMBON = pro(11, "Dr Nadia Benslimane", 45.7222, 4.8800),
            // Même immeuble que CHARITE : départage par le nom.
            CHARITE_BIS = pro(12, "Dr Anne Bis", 45.7558, 4.8330);
    ProfessionalRepository repo = mock(ProfessionalRepository.class);
    NearbyService service = new NearbyService(new BoxNearbyFinder(repo));

    {
        when(repo.findInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(CAMBON, REPUBLIQUE, CHARITE, LAFAYETTE, CHARITE_BIS));
    }

    static List<String> names(NearbyService.Page p) {
        return p.items().stream().map(f -> f.professional().name).toList();
    }

    @Test
    void keepsCircleNotSquare() {
        var page = service.search(NearbyQuery.of(45.7576, 4.8317, null, "", 0, 10));
        assertThat(names(page)).doesNotContain("Dr Nadia Benslimane");
        assertThat(page.items()).allSatisfy(f -> assertThat(f.distanceKm()).isLessThanOrEqualTo(5));
    }

    @Test
    void sortsByDistanceThenName() {
        assertThat(names(service.search(NearbyQuery.of(45.7576, 4.8317, null, "", 0, 10))))
                .containsExactly(
                        "Dr Amel Benali", "Dr Anne Bis", "Dr Hélène Durand", "Dr Hugo Petit");
    }

    @Test
    void sortsThenSlicesPage() {
        var p1 = service.search(NearbyQuery.of(45.7576, 4.8317, null, "", 1, 2));
        assertThat(names(p1)).containsExactly("Dr Hélène Durand", "Dr Hugo Petit");
        assertThat(p1.total()).isEqualTo(4);
        assertThat(service.search(NearbyQuery.of(45.7576, 4.8317, null, "", 5, 2)).items())
                .isEmpty();
    }

    @Test
    void hugePageGivesEmptyPageNotError() {
        var p = service.search(NearbyQuery.of(45.7576, 4.8317, null, "", Integer.MAX_VALUE, 50));
        assertThat(p.items()).isEmpty();
        assertThat(p.total()).isEqualTo(4);
    }

    @Test
    void boxSentToDatabaseSurroundsBellecour() {
        service.search(NearbyQuery.of(45.7576, 4.8317, null, "cardiology", 0, 6));
        verify(repo)
                .findInBox(
                        doubleThat(v -> v < 45.7576),
                        doubleThat(v -> v > 45.7576),
                        doubleThat(v -> v < 4.8317),
                        doubleThat(v -> v > 4.8317),
                        eq("cardiology"));
    }
}
