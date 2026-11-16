-- Position du cabinet (WGS 84, degrés décimaux). Nulle tant qu'elle n'est pas géocodée : le praticien n'apparaît pas dans « Autour de moi ».
ALTER TABLE professional ADD COLUMN latitude DOUBLE PRECISION CHECK (latitude BETWEEN -90 AND 90);
ALTER TABLE professional ADD COLUMN longitude DOUBLE PRECISION CHECK (longitude BETWEEN -180 AND 180);
ALTER TABLE professional ADD CONSTRAINT professional_position_complete CHECK ((latitude IS NULL) = (longitude IS NULL));
-- La recherche filtre d'abord sur la boîte englobante : latitude puis longitude
CREATE INDEX professional_position_idx ON professional (latitude, longitude);
-- Positions des praticiens de démonstration déjà en base (adresses géocodées avec la Géoplateforme IGN, arrondies à 10 m)
UPDATE professional SET latitude=48.8663, longitude=2.3685 WHERE name='Dr Sarah Martin' AND address='18 avenue de la République';
UPDATE professional SET latitude=48.8487, longitude=2.3285 WHERE name='Dr Thomas Leroy' AND address='42 rue de Rennes';
UPDATE professional SET latitude=45.7558, longitude=4.8330 WHERE name='Dr Amel Benali' AND address='6 rue de la Charité';
UPDATE professional SET latitude=45.7637, longitude=4.8446 WHERE name='Dr Hugo Petit' AND address='21 cours Lafayette';
UPDATE professional SET latitude=48.8496, longitude=2.3547 WHERE name='Dr Léa Moreau' AND address='12 boulevard Saint-Germain';
UPDATE professional SET latitude=44.8455, longitude=-0.5775 WHERE name='Dr Inès Diallo' AND address='8 cours de Verdun';
