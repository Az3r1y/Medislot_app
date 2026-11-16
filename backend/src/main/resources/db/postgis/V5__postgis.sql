-- Défi PostGIS (activé par compose.postgis.yaml) : la position devient un point géographique indexé.
CREATE EXTENSION IF NOT EXISTS postgis;
-- Colonne calculée : toujours cohérente avec latitude/longitude, jamais écrite par l'application. ST_MakePoint(x = lon, y = lat).
ALTER TABLE professional ADD COLUMN location geography(Point, 4326)
  GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;
CREATE INDEX professional_location_gix ON professional USING GIST (location);
