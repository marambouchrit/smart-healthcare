# CLAUDE.md — Règles de travail pour Claude sur le projet Smart Healthcare

Projet individuel du module Service Oriented Computing (3ᵉ année ING GINF, 2026-2027).
Ce fichier est lu automatiquement au début de chaque session. Ses règles priment sur les habitudes par défaut.

## 1. Sources de vérité (dans cet ordre)

| Sujet | Fichier | Rôle |
|---|---|---|
| Exigences | `docs/sujet/SUJET.md` (et le PDF original dans `docs/sujet/`) | Ce que le projet DOIT contenir |
| Plan et avancement | `ROADMAP.md` | Tâches, ordre, état réel |
| Choix techniques | `docs/DECISIONS.md` | Stack, ports, routes, conventions, questions ouvertes |
| Historique | `docs/JOURNAL.md` | Ce qui a été fait, quand, comment c'est vérifié |
| Contrats | `contracts/` | Interfaces officielles des services : le code s'aligne sur eux |

Si une information n'est dans aucun de ces fichiers, elle n'est pas décidée : demander à l'utilisateur.

## 2. Début de chaque session (obligatoire)

1. Lire `ROADMAP.md`, `docs/DECISIONS.md` et les 3 dernières entrées de `docs/JOURNAL.md`.
2. Exécuter `node scripts/roadmap-status.mjs` (le hook SessionStart le fait déjà si `.claude/settings.json` est actif ; sinon le lancer).
3. Annoncer en 3 lignes maximum : phase en cours, tâche suivante (première `[~]`, sinon première `[ ]` dans l'ordre), et lien avec la demande de l'utilisateur.
4. Si la demande ne correspond à aucune tâche de la roadmap, le dire et demander s'il faut l'ajouter avant de commencer.

## 3. Pendant le travail

- Une tâche à la fois, dans l'ordre des phases. Ne pas commencer la phase 6 (bonus) tant que les phases 0 à 5 ne sont pas toutes `[x]`.
- Passer la tâche à `[~]` dans `ROADMAP.md` dès qu'elle commence.
- Respecter les contrats de `contracts/`. Si un contrat doit changer : expliquer pourquoi, demander l'accord, puis modifier le contrat AVANT le code et noter le changement dans le journal.
- Ne jamais renommer un identifiant de tâche, un port, une route ou un namespace défini dans `docs/DECISIONS.md` sans accord.

## 4. Fin d'une tâche (obligatoire)

1. Exécuter réellement la vérification (test, commande, requête) et montrer la sortie obtenue.
2. Cocher `[x]` seulement si la vérification réussit. Sinon laisser `[~]` et écrire ce qui bloque.
3. Ajouter une entrée dans `docs/JOURNAL.md` avec le modèle prévu (date, tâches, fait, vérification, reste à faire).
4. Proposer un commit au format : `P2.REST.2: CRUD complet des rendez-vous`.
5. Relancer `node scripts/roadmap-status.mjs` et annoncer la tâche suivante.

## 5. Règles anti-invention

- **Exigences** : ne rien ajouter qui ne soit pas dans `docs/sujet/SUJET.md`. Une idée hors sujet se propose comme bonus (phase 6), elle ne s'implémente pas sans accord.
- **Résultats** : ne jamais dire qu'un test passe, qu'un service démarre ou qu'un fichier existe sans l'avoir vérifié dans cette session.
- **Versions et API de librairies** : ne pas les écrire de mémoire. Vérifier (documentation officielle, Maven Central, npm, PyPI, fichiers du projet) ou demander. Une fois choisie, une version est notée dans `docs/DECISIONS.md`.
- **Données** : utiliser uniquement les données de `contracts/seed/`. Pas de nouvel identifiant patient, médecin ou code CNAM inventé ailleurs.
- **Code existant** : lire un fichier avant de le modifier ; ne pas supposer son contenu.
- **Doute** : poser une question courte plutôt que deviner.

## 6. Référentiel technique (résumé — le détail fait foi dans `docs/DECISIONS.md`)

| Composant | Dossier | Techno | Port |
|---|---|---|---|
| API Gateway | `gateway/` | Spring Cloud Gateway | 8080 (seul port exposé) |
| Service SOAP — Dossier & CNAM | `services/soap-dossier/` | Spring Boot 3 + JAX-WS (Apache CXF) | 8081 |
| Service REST — Portail & RDV | `services/rest-portail/` | Spring Boot 3 + Spring MVC + Spring Data JPA + springdoc | 8082 |
| Service GraphQL — Tableau de bord | `services/graphql-dashboard/` | Spring for GraphQL | 8083 |
| Orchestrateur | `orchestrator/` | Spring Boot 3 | 8084 |
| Service gRPC — Télémétrie | `services/grpc-telemetrie/` | Python + grpcio | 50051 |
| Capteurs IoT virtuels | `iot-simulator/` | Script Python | — |
| Client web | `web-client/` | Angular | 4200 |
| Bases de données | `docker-compose.yml` | PostgreSQL, 1 conteneur, 1 base par service | 5432 |

Conventions :
- Identifiant patient commun à tous les services : `TN-` suivi de 7 chiffres.
- Namespace SOAP : `http://hopital.tn/dossier`.
- Packages Java : `tn.ginf.smarthealthcare.<service>`.
- Aucun service n'appelle un autre service ni ne lit sa base : seul l'orchestrateur enchaîne les appels (couplage lâche).
- Le tableau de bord GraphQL est une vue consolidée alimentée par l'orchestrateur (option A).

## 7. Commandes du projet

| Commande | Effet | Disponible |
|---|---|---|
| `node scripts/roadmap-status.mjs` | Résumé de l'avancement et tâche suivante | Oui |
| `node scripts/check-seed.mjs` | Contrôle des données fictives de `contracts/seed/` | Oui |
| `java scripts/ValidateXsd.java <xsd> <xml>...` | Valide des messages XML contre un XSD (JDK seul) | Oui |
| `python scripts/check_wsdl.py <wsdl> <xsd>` | Charge le WSDL avec zeep et vérifie chaque opération (prérequis : `pip install zeep==4.3.3`) | Oui |
| `python scripts/check_proto.py` | Compile `telemetrie.proto` et vérifie le contrat gRPC (prérequis : `pip install grpcio-tools==1.85.1`) | Oui |
| `python scripts/check_graphql.py` | Charge le schéma GraphQL et valide les exemples (prérequis : `pip install graphql-core==3.3.2`) | Oui |
| `python scripts/check_openapi.py [fichier]` | Valide le contrat OpenAPI et sa cohérence avec le seed (prérequis : `pip install openapi-spec-validator==0.9.0`) | Oui |
| `/avancement` | Point d'avancement détaillé (commande Claude) | Oui |
| `/tache-suivante` | Prépare et démarre la prochaine tâche | Oui |
| `/terminer-tache` | Vérifie, coche, met à jour le journal | Oui |
| `docker compose up -d postgres` | Démarre PostgreSQL (port 5433, une base par service) — prérequis des services persistés et de leurs tests | Oui |
| `cd services/soap-dossier && ./mvnw test` | Génère le code depuis le WSDL, compile et lance les tests du service SOAP | Oui |
| `cd services/soap-dossier && ./mvnw package -DskipTests && java -jar target/soap-dossier-0.1.0.jar` | Lance le service SOAP sur le port 8081 (`/ws/dossier?wsdl`) | Oui |
| `cd services/rest-portail && ./mvnw test` | Tests du service REST | Oui |
| `cd services/rest-portail && ./mvnw package -DskipTests && java -jar target/rest-portail-0.1.0.jar` | Lance le service REST sur le port 8082 | Oui |
| `cd services/graphql-dashboard && ./mvnw test` | Tests du service GraphQL | Oui |
| `cd services/graphql-dashboard && ./mvnw package -DskipTests && java -jar target/graphql-dashboard-0.1.0.jar` | Lance le service GraphQL sur le port 8083 (GraphiQL : `/graphiql`) | Oui |
| `cd services/grpc-telemetrie && python -m venv .venv && .venv/Scripts/pip install -r requirements.txt && .venv/Scripts/python generer.py` | Prépare le service gRPC (environnement + code généré depuis le `.proto`) | Oui |
| `cd services/grpc-telemetrie && .venv/Scripts/python -m unittest discover -s tests -t . -v` | Tests du service gRPC (ceux de persistance demandent `docker compose up -d postgres`) | Oui |
| `cd services/grpc-telemetrie && .venv/Scripts/python -m telemetrie.serveur` | Lance le service gRPC sur le port 50051 | Oui |
| `cd iot-simulator && python -m venv .venv && .venv/Scripts/pip install -r requirements.txt && .venv/Scripts/python generer.py` | Prépare le simulateur de capteurs | Oui |
| `cd iot-simulator && .venv/Scripts/python -m capteur.simulateur --patient TN-0012345 --critique-a 5` | Envoie des mesures et injecte une valeur critique (options : `--help`) | Oui |
| `cd orchestrator && ./mvnw test` | Tests du workflow de l'orchestrateur | Oui |
| `cd orchestrator && ./mvnw package -DskipTests && java -jar target/orchestrator-0.1.0.jar` | Lance l'orchestrateur (port 8084) ; il s'abonne aux alertes gRPC | Oui |
| `http://localhost:8082/swagger-ui.html` | Swagger UI du service REST (affiche le contrat ; choisir le serveur 8082 pour « Try it out ») | Oui |
| `cd iot-simulator && .venv/Scripts/python -m capteur.simulateur --scenario scenarios/demo-alertes.json` | Scénario multi-patients (autres : `normal.json`, `afflux.json`) | Oui |
| `docker build -f services/soap-dossier/Dockerfile -t smart-healthcare/soap-dossier:0.1.0 .` | Construit l'image d'un composant (même forme pour rest-portail, graphql-dashboard, grpc-telemetrie, orchestrator, iot-simulator) ; à lancer depuis la racine | Oui |
| `docker compose up -d --build` | Construit et lance PostgreSQL, les 5 services et la gateway (seul port publié : `${GATEWAY_PORT:-8080}`, 8090 sur ce poste via `.env`) | Oui |
| `cd gateway && ./mvnw test` | Tests des routes et de l'authentification JWT de la gateway | Oui |
| `curl -X POST -H 'Content-Type: application/json' -d '{"identifiant":"medecin","motDePasse":"medecin_dev"}' http://localhost:8090/auth/login` | Obtient un jeton (comptes `patient`, `medecin`, `admin`, D44) à envoyer en `Authorization: Bearer <jeton>` | Oui |
| `cd gateway && ./mvnw package -DskipTests && java -jar target/gateway-0.1.0.jar` | Lance la gateway hors Docker (port 8080, services sur localhost:8081–8084) | Oui |
| `docker compose run --rm -e SCENARIO=scenarios/demo-alertes.json iot-simulator` | Lance les capteurs IoT dans Docker (profil `demo`) | Oui |
| `curl -N "http://localhost:8090/events?access_token=<jeton>"` | Suit en direct le flux SSE des alertes et des prises en charge (format : `contracts/sse/README.md`) | Oui |
| `docker compose logs -f orchestrator` / `docker compose down` | Suit les workflows / arrête la plateforme (`-v` efface les données) | Oui |

Ajouter ici chaque nouvelle commande (build, tests) au moment où elle est créée.
