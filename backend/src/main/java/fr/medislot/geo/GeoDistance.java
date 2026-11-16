package fr.medislot.geo;

/** Distance à vol d'oiseau (haversine, Terre sphérique : moins de 0,5 % d'écart). */
public final class GeoDistance {
    /** Rayon moyen de la Terre (IUGG), en kilomètres. */
    public static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistance() {}

    /** Arguments dans l'ordre latitude, longitude, en degrés décimaux. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1),
                p2 = Math.toRadians(lat2),
                dp = Math.toRadians(lat2 - lat1),
                dl = Math.toRadians(lon2 - lon1);
        double h =
                Math.pow(Math.sin(dp / 2), 2)
                        + Math.cos(p1) * Math.cos(p2) * Math.pow(Math.sin(dl / 2), 2);
        // min(1, …) : arrondis flottants aux antipodes.
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Au dixième de kilomètre : assez précis, sans trahir la position. */
    public static double roundKm(double km) {
        return Math.round(km * 10) / 10.0;
    }
}
