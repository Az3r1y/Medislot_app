package fr.medislot.geo;

/** Boîte englobante d'un cercle, en degrés. minLon > maxLon : elle traverse l'antiméridien. */
public record BoundingBox(double minLat, double maxLat, double minLon, double maxLon) {
    public static BoundingBox around(double lat, double lon, double radiusKm) {
        double dLat = Math.toDegrees(radiusKm / GeoDistance.EARTH_RADIUS_KM);
        double minLat = lat - dLat, maxLat = lat + dLat;
        // Un pôle est dans le cercle : toutes les longitudes.
        if (maxLat >= 90 || minLat <= -90)
            return new BoundingBox(Math.max(minLat, -90), Math.min(maxLat, 90), -180, 180);
        // Un degré de longitude rétrécit avec la latitude.
        double dLon =
                Math.toDegrees(
                        radiusKm / (GeoDistance.EARTH_RADIUS_KM * Math.cos(Math.toRadians(lat))));
        if (dLon >= 180) return new BoundingBox(minLat, maxLat, -180, 180);
        double minLon = lon - dLon, maxLon = lon + dLon;
        if (minLon < -180) minLon += 360;
        if (maxLon > 180) maxLon -= 360;
        return new BoundingBox(minLat, maxLat, minLon, maxLon);
    }

    public boolean crossesAntimeridian() {
        return minLon > maxLon;
    }

    public boolean contains(double lat, double lon) {
        return lat >= minLat
                && lat <= maxLat
                && (crossesAntimeridian()
                        ? lon >= minLon || lon <= maxLon
                        : lon >= minLon && lon <= maxLon);
    }
}
