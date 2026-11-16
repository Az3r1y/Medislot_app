-- Connexion avec Google : le compte est relié à l'identifiant stable « sub » du jeton OpenID,
-- jamais à l'adresse e-mail seule. Un compte Google n'a pas de mot de passe local.
-- V1 à V9 sont conservées ; cette migration complète le socle multi-cabinet et calendrier.
ALTER TABLE app_user ADD COLUMN google_subject VARCHAR(255) UNIQUE;
ALTER TABLE app_user ALTER COLUMN password DROP NOT NULL;
ALTER TABLE app_user ADD CONSTRAINT app_user_has_credential CHECK (password IS NOT NULL OR google_subject IS NOT NULL);
