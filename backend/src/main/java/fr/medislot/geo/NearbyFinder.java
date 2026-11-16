package fr.medislot.geo;

import fr.medislot.domain.Professional;

import java.util.List;

/** Praticiens dans le cercle avec leur distance, sans ordre particulier. */
public interface NearbyFinder {
    record Found(Professional professional, double distanceKm) {}

    List<Found> find(NearbyQuery q);
}
