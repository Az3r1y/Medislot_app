# MediSlot

Application de prise de rendez-vous médicaux : recherche de praticiens, consultation des disponibilités et gestion des rendez-vous depuis une interface web.

Le projet utilise des praticiens et des comptes fictifs pour la démonstration.

## Fonctionnalités

- Recherche par nom, spécialité et ville, avec pagination.
- Recherche de praticiens dans un rayon de 5 km, depuis la position du navigateur ou une adresse.
- Carte des résultats avec Leaflet et OpenStreetMap ; géocodage des adresses via la Géoplateforme IGN.
- Fiches des praticiens et disponibilités affichées dans le fuseau `Europe/Paris`.
- Inscription, connexion et déconnexion par session, avec protection CSRF.
- Réservation, déplacement et annulation des rendez-vous.
- Historique des opérations, notifications et mise à jour des disponibilités en temps réel par SSE.
- Téléchargement d’une convocation PDF.
- Export de rendez-vous au format iCalendar et abonnement personnel à un calendrier en lecture seule.
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

Pour la démonstration locale, `compose.yaml` active les données fictives, autorise les cookies en HTTP et utilise `medislot-local-only` comme mot de passe PostgreSQL si `DB_PASSWORD` n’est pas défini.

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

Le lien d'abonnement calendrier contient un secret. Il est affiché uniquement lors de sa création et peut être révoqué ou régénéré depuis « Mes rendez-vous ». Le téléchargement ICS est ponctuel ; le client calendrier choisit la fréquence de mise à jour d'un abonnement. Les journaux des éventuels proxys supplémentaires doivent exclure la chaîne de requête de `/api/calendar/feed.ics`.
