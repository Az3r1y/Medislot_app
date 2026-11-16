package fr.medislot.geo;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Paramètres de la recherche par position, validés dans l'ordre du contrat. */
public record NearbyQuery(
        double lat, double lon, double radiusKm, String specialty, int page, int size) {
    public static final double DEFAULT_RADIUS_KM = 5, MIN_RADIUS_KM = 0.1, MAX_RADIUS_KM = 50;
    public static final int MAX_SIZE = 50;
    static final String POSITION_INCOMPLETE = "Position incomplète : indiquez lat et lon ensemble.";
    static final String LAT_OUT = "Latitude hors bornes : lat doit être entre -90 et 90.";
    static final String LON_OUT = "Longitude hors bornes : lon doit être entre -180 et 180.";
    static final String RADIUS_OUT = "Rayon hors bornes : radiusKm doit être entre 0,1 et 50.";
    static final String PAGE_OUT =
            "Pagination invalide : page doit être positive ou nulle, size entre 1 et 50.";

    public static NearbyQuery of(
            Double lat, Double lon, Double radiusKm, String specialty, int page, int size) {
        if (lat == null || lon == null) throw bad(POSITION_INCOMPLETE);
        // NaN et l'infini passent la conversion en Double.
        if (!Double.isFinite(lat) || lat < -90 || lat > 90) throw bad(LAT_OUT);
        if (!Double.isFinite(lon) || lon < -180 || lon > 180) throw bad(LON_OUT);
        // Plafonné pour ne pas balayer tout le pays.
        double r = radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
        if (!Double.isFinite(r) || r < MIN_RADIUS_KM || r > MAX_RADIUS_KM) throw bad(RADIUS_OUT);
        if (page < 0 || size < 1 || size > MAX_SIZE) throw bad(PAGE_OUT);
        return new NearbyQuery(lat, lon, r, specialty == null ? "" : specialty, page, size);
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
