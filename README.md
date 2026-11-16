# MediSlot

Application de prise de rendez-vous médicaux : recherche de praticiens, consultation des disponibilités et gestion des rendez-vous depuis une interface web.

Le projet utilise des praticiens et des comptes fictifs pour la démonstration.

## Fonctionnalités

- Recherche par nom, spécialité et ville, avec pagination.
- Recherche de praticiens dans un rayon de 5 km, depuis la position du navigateur ou une adresse.
- Carte des résultats avec Leaflet et OpenStreetMap ; géocodage des adresses via la Géoplateforme IGN.
- Fiches des praticiens et disponibilités affichées dans le fuseau `Europe/Paris`.
- Inscription, connexion et déconnexion par session, avec protection CSRF.
- Connexion avec Google (OpenID Connect), activée lorsque des identifiants Google sont fournis.
- Réservation, déplacement et annulation des rendez-vous.
- Historique des opérations, notifications et mise à jour des disponibilités en temps réel par SSE.
- Téléchargement d’une convocation PDF.
- Expérience A/B sur la confirmation de réservation.
- Traces distribuées avec OpenTelemetry et Jaeger.

## Technologies

| Partie | Technologies |
|---|---|
| Backend | Java 25, Spring Boot 4.1.2, Spring Security, Spring Data JPA |
| Frontend | Vue 3, TypeScript, Pinia, Vite |
| Base de données | PostgreSQL 18, migrations Flyway ; variante PostGIS |
| Documents | Thymeleaf, OpenHTMLtoPDF |
| Observabilité | OpenTelemetry Collector, Jaeger |
| Tests | JUnit, Testcontainers, Vitest, Playwright |
| Exécution | Docker Compose, Nginx |

## Démarrage avec Docker

Depuis la racine du dépôt, avec Docker et Docker Compose installés :

```sh
docker compose up -d --build
```

| Service | Adresse |
|---|---|
| Application | [localhost:8088](http://localhost:8088) |
| Santé du backend | [localhost:18080/actuator/health](http://localhost:18080/actuator/health) |
| Jaeger | [localhost:16686](http://localhost:16686) |
| PostgreSQL | `127.0.0.1:55432` |

Deux comptes patients sont disponibles :

| Adresse e-mail | Mot de passe |
|---|---|
| `patient@medislot.test` | `MediSlot-demo-2026!` |
| `autre@medislot.test` | `MediSlot-demo-2026!` |

Il est aussi possible de créer un compte depuis l’interface.

Les données sont conservées dans un volume Docker. Les créneaux de démonstration couvrent les dix jours suivant leur initialisation. Pour renouveler une démonstration, renouveler les créneaux en base ou démarrer un projet Compose distinct après avoir arrêté le précédent.

Commandes utiles :

```sh
docker compose ps
docker compose logs -f backend
docker compose down
```

`docker compose down` arrête les services et conserve le volume de la base.

## Développement local

Prérequis : Java 25, Node.js 24 et Docker pour PostgreSQL. Le wrapper Maven est fourni dans `backend/` ; Maven 3.9 peut aussi être utilisé.

### Base de données

```sh
docker compose up -d db
```

### Backend

Dans un autre terminal, depuis la racine du dépôt :

```sh
cd backend
export DATABASE_URL=jdbc:postgresql://localhost:55432/medislot
export DB_PASSWORD=medislot-local-only
export DEMO_SEED=true
export COOKIE_SECURE=false
export PORT=18080
sh mvnw spring-boot:run
```

Si `DB_PASSWORD` a été personnalisé pour Docker Compose, utiliser la même valeur ici. Les commandes `export` ci-dessus sont prévues pour Bash ou Zsh ; sous PowerShell, utiliser par exemple `$env:PORT='18080'`, puis `./mvnw.cmd spring-boot:run`.

### Frontend

Dans un autre terminal, depuis la racine du dépôt :

```sh
cd frontend
npm ci
npm run dev
```

Ouvrir [127.0.0.1:5173](http://127.0.0.1:5173). Vite transmet les requêtes `/api` au backend sur le port `18080`. La variable `API_TARGET` permet de changer cette destination.

La chaîne de traces complète, avec l’agent Java, le collecteur et Jaeger, est configurée dans la stack Docker Compose.

## Configuration

| Variable | Rôle | Valeur par défaut de l’application |
|---|---|---|
| `DATABASE_URL` | Connexion JDBC | `jdbc:postgresql://localhost:5432/medislot` |
| `DB_USER` | Utilisateur PostgreSQL du backend | `medislot` |
| `DB_PASSWORD` | Mot de passe PostgreSQL | Obligatoire |
| `PORT` | Port HTTP du backend | `8080` |
| `DEMO_SEED` | Initialisation des données fictives | `false` |
| `COOKIE_SECURE` | Cookies réservés aux connexions HTTPS | `true` |
| `AB_ENABLED` | Activation de l’expérience A/B | `true` |
| `GOOGLE_CLIENT_ID` | Identifiant du client OAuth Google ; vide : connexion Google désactivée | vide |
| `GOOGLE_CLIENT_SECRET` | Secret du client OAuth Google, obligatoire avec `GOOGLE_CLIENT_ID` | vide |
| `GOOGLE_REDIRECT_URI` | URI de retour déclarée chez Google | adresse vue par le backend |

Pour la démonstration locale, `compose.yaml` active les données fictives, autorise les cookies en HTTP et utilise `medislot-local-only` comme mot de passe PostgreSQL si `DB_PASSWORD` n’est pas défini.

### Connexion avec Google

La connexion avec Google est facultative. Sans `GOOGLE_CLIENT_ID`, l’application démarre normalement et le bouton « Continuer avec Google » n’est pas affiché.

1. Dans la [console Google Cloud](https://console.cloud.google.com/apis/credentials), configurer l’écran de consentement OAuth, puis créer un identifiant « ID client OAuth » de type « Application Web ».
2. Ajouter les URI de redirection autorisées, selon l’environnement :
   - Docker Compose : `http://localhost:8088/api/login/oauth2/code/google`
   - Développement avec Vite : `http://127.0.0.1:5173/api/login/oauth2/code/google`
3. Copier `.env.example` en `.env` à la racine du dépôt et renseigner `GOOGLE_CLIENT_ID` et `GOOGLE_CLIENT_SECRET`. Le fichier `.env` est lu par Docker Compose et n’est pas versionné.
4. Relancer la stack : `docker compose up -d --build`.

En développement local, exporter les mêmes variables avant de démarrer le backend, avec `GOOGLE_REDIRECT_URI=http://127.0.0.1:5173/api/login/oauth2/code/google`. L’URI doit correspondre exactement à celle déclarée chez Google.

Fonctionnement :

- Le parcours passe par `/api/oauth2/authorization/google`, puis Google rappelle `/api/login/oauth2/code/google`. Spring Security vérifie `state`, `nonce`, PKCE et la signature du jeton OpenID.
- Le compte MediSlot est relié à l’identifiant `sub` de Google, stable même si l’adresse e-mail change. À la première connexion, un compte patient sans mot de passe est créé si Google indique que l’adresse est vérifiée.
- Un compte existant créé avec un mot de passe n’est jamais relié automatiquement à Google, car l’inscription locale ne vérifie pas l’adresse e-mail. La personne est invitée à se connecter avec son mot de passe.
- Un compte créé avec Google ne peut pas se connecter par mot de passe.

### Variante PostGIS

Arrêter la stack standard avant de lancer cette variante, car elle utilise les mêmes ports. Employer un nom de projet distinct pour disposer d’un volume séparé :

```sh
docker compose down
docker compose -p medislot-postgis -f compose.yaml -f compose.postgis.yaml up -d --build
```

Cette configuration active les migrations PostGIS et la recherche spatiale avec `ST_DWithin`. L’image PostGIS est configurée pour `linux/amd64` et peut nécessiter une émulation sur une machine ARM.

## Vérifications

Tests backend, depuis la racine du dépôt, avec Java 25 et Docker disponibles :

```sh
sh backend/mvnw -f backend/pom.xml verify
```

Tests frontend et compilation, depuis `frontend/` :

```sh
npm ci
npm test
npm run build
npm run format:check
```

Tests de bout en bout, depuis `frontend/`, avec la stack de démonstration démarrée :

```sh
npx playwright install chromium
npm run test:e2e
```

Les tests Playwright ciblent `http://localhost:8088` par défaut. La variable `APP_URL` permet de tester une autre instance. Ils créent et annulent des rendez-vous dans les données de démonstration.

Depuis la racine, le script suivant vérifie la chaîne de traces de la stack Docker :

```sh
bash tests/verifier_traces.sh
```

## Organisation du dépôt

| Chemin | Contenu |
|---|---|
| `backend/src/main/` | API, logique métier, sécurité, migrations SQL et template PDF |
| `backend/src/test/` | Tests Java |
| `frontend/src/` | Composants Vue, composables et tests unitaires |
| `frontend/e2e/` | Tests Playwright |
| [docs/openapi.yaml](docs/openapi.yaml) | Contrat de l’API |
| `observabilite/` | Configuration du collecteur OpenTelemetry |
| `tests/` | Scripts de vérification des traces et de la convocation |
| `.github/workflows/` | Vérification et construction des livraisons |

## Intégration continue

Le workflow `ci.yml` compile et teste le backend, vérifie le frontend et exécute les tests de bout en bout. Il se déclenche sur les pushes vers `main` et les pull requests.

Le workflow `release.yml`, déclenché manuellement, relance les vérifications puis construit les images Docker et les publie comme artefacts GitHub Actions. Il utilise l’environnement GitHub `staging`. Le déploiement chez un hébergeur reste à configurer.

## Limites

L’interface couvre le parcours patient. L’administration des praticiens et de leurs horaires n’est pas fournie. Les abonnements SSE sont gérés par chaque instance du backend et ne proposent pas de rejeu historique.

Les comptes et réglages Docker fournis servent à la démonstration. Une utilisation réelle nécessite notamment une configuration des secrets, HTTPS, des sauvegardes et une évaluation des exigences applicables aux données de santé. Ne pas ajouter de données réelles de patients à cet environnement.
