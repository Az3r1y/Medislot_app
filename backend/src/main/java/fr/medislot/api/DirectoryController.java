package fr.medislot.api;

import fr.medislot.domain.*;
import fr.medislot.geo.*;
import fr.medislot.repository.*;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;

@RestController
@RequestMapping("/api/professionals")
@org.springframework.transaction.annotation.Transactional
public class DirectoryController {
    private final ProfessionalRepository professionals;
    private final SlotRepository slots;
    private final NearbyService nearby;

    public DirectoryController(
            ProfessionalRepository professionals, SlotRepository slots, NearbyService nearby) {
        this.professionals = professionals;
        this.slots = slots;
        this.nearby = nearby;
    }

    // distanceKm n'est renseigné qu'en recherche par position.
    public record ProfessionalDto(
            Long id,
            String name,
            String specialty,
            String city,
            String address,
            String bio,
            int fee,
            Double latitude,
            Double longitude,
            Double distanceKm) {
        static ProfessionalDto from(Professional p) {
            return of(p, null);
        }

        static ProfessionalDto of(Professional p, Double distanceKm) {
            return new ProfessionalDto(
                    p.id,
                    p.name,
                    p.specialty,
                    p.city,
                    p.address,
                    p.bio,
                    p.fee,
                    p.latitude,
                    p.longitude,
                    distanceKm);
        }
    }

    public record PageDto<T>(java.util.List<T> items, int page, int size, long total) {}

    public record SlotDto(Long id, Long professionalId, Instant startsAt, Instant endsAt) {}

    // Avec lat et lon : recherche par distance, city et q sont ignorés.
    // La position n'est ni stockée ni journalisée.
    @GetMapping
    public PageDto<ProfessionalDto> search(
            @RequestParam(defaultValue = "") String specialty,
            @RequestParam(defaultValue = "") String city,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) Double radiusKm) {
        if (lat != null || lon != null || radiusKm != null) {
            if (specialty.length() > 60)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Paramètres de recherche invalides.");
            var query = NearbyQuery.of(lat, lon, radiusKm, specialty, page, size);
            var found = nearby.search(query);
            return new PageDto<>(
                    found.items().stream()
                            .map(
                                    f ->
                                            ProfessionalDto.of(
                                                    f.professional(),
                                                    GeoDistance.roundKm(f.distanceKm())))
                            .toList(),
                    page,
                    size,
                    found.total());
        }
        if (page < 0
                || size < 1
                || size > 50
                || city.length() > 80
                || q.length() > 120
                || specialty.length() > 60)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Paramètres de recherche invalides.");
        var found =
                professionals.search(
                        specialty,
                        city,
                        q,
                        PageRequest.of(page, size, Sort.by("name").and(Sort.by("id"))));
        return new PageDto<>(
                found.getContent().stream().map(ProfessionalDto::from).toList(),
                page,
                size,
                found.getTotalElements());
    }

    @GetMapping("/{id}")
    public ProfessionalDto one(@PathVariable Long id) {
        return ProfessionalDto.from(
                professionals
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Professionnel introuvable.")));
    }

    @GetMapping("/{id}/slots")
    public java.util.List<SlotDto> slots(@PathVariable Long id) {
        if (!professionals.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Professionnel introuvable.");
        Instant now = Instant.now();
        return slots.available(id, now, now.plus(Duration.ofDays(14))).stream()
                .map(s -> new SlotDto(s.id, s.professionalId, s.startsAt, s.endsAt))
                .toList();
    }
}
