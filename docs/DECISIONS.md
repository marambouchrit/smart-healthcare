# Décisions techniques

Toute décision prise avec l'utilisateur est notée ici. Une décision ne change que sur demande explicite ; dans ce cas on ajoute une nouvelle entrée qui remplace l'ancienne (on ne supprime pas l'historique).

## Décisions prises

| # | Date | Décision | Raison |
|---|---|---|---|
| D1 | 2026-11-17 | Projet **individuel** | Contexte de l'étudiante |
| D2 | 2026-11-17 | Stack : Java 17 + Spring Boot 3 pour SOAP, REST, GraphQL, orchestrateur et gateway ; Python pour gRPC et capteurs IoT | Cohérent avec le cours (JAX-WS, Spring MVC) ; un second langage illustre l'interopérabilité |
| D3 | 2026-11-17 | Client web en **Angular** (au lieu de React) | Choix de l'étudiante |
| D4 | 2026-11-17 | Service SOAP en JAX-WS via Apache CXF, approche **top-down** (WSDL écrit d'abord) | Annotations du chapitre 4 ; contrat de type institutionnel imposé |
| D5 | 2026-11-17 | Tableau de bord GraphQL = **option A** : vue consolidée propre, alimentée par l'orchestrateur via mutations | Couplage lâche : aucun service n'appelle un autre service |
| D6 | 2026-11-17 | Le navigateur ne parle pas gRPC : l'orchestrateur consomme le flux d'alertes et pousse au client en **SSE** via la gateway | Les navigateurs ne supportent pas gRPC natif ; évite un proxy Envoy |
| D7 | 2026-11-17 | Gateway : **Spring Cloud Gateway** | Même écosystème Spring, routes en YAML |
| D8 | 2026-11-17 | **PostgreSQL** : un conteneur, une base par service | Respecte « une base par microservice » sans multiplier les conteneurs |
| D9 | 2026-11-17 | Orchestration **codée** (service Spring) ; le processus est documenté en BPMN dans le rapport | Plus simple qu'un moteur BPMN pour un projet individuel |
| D10 | 2026-11-17 | Méthode : contrats d'abord, puis squelette de bout en bout, puis enrichissement | Réduit le risque d'intégration tardive |
| D11 | 2026-11-17 | Bonus (N.B. du sujet) réalisés **après** les phases 0 à 5 | Livrer d'abord un socle complet |
| D12 | 2026-11-17 | Roadmap **sans dates ni durées** | Choix de l'étudiante |
| D13 | 2026-11-17 | Données fictives en **JSON** dans `contracts/seed/` ; identifiants `MED-xxx`, `TRT-xxx`, vignettes `VIG-AAAA-xxxxxx` ; lettres-clés C, CS, V, K, B, Z, AMI avec valeurs en dinars **fictives** | Lisible par Java et Python sans dépendance ; pas de nomenclature officielle disponible |
| D14 | 2026-11-17 | Fins de ligne **LF** imposées par `.gitattributes` (CRLF seulement pour `.bat`, `.cmd`, `.ps1`) | Les scripts exécutés dans les conteneurs Linux cassent avec des fins de ligne CRLF |
| D15 | 2026-11-17 | **Java 21** pour tous les composants Java (remplace « Java 17 » de D2 ; le reste de D2 est inchangé) | JDK 21.0.9 déjà installé ; compatible Spring Boot 3 (réponse à Q3) |
| D16 | 2026-11-17 | Opération SOAP de comparaison nommée **`comparerGaranties`** : deux prises en charge (patient, lettre-clé, coefficient, date) → taux, montant remboursé de chacune et écart | Le sujet ne nomme pas l'opération |
| D17 | 2026-11-17 | Code Java généré depuis `telemetrie.proto` dans le package `tn.ginf.smarthealthcare.telemetrie` (`java_multiple_files = true`) | Suit la convention `tn.ginf.smarthealthcare.<...>` sans lier le contrat à un consommateur |
| D18 | 2026-11-17 | 4ᵉ mutation GraphQL **`enregistrerConstantes`** appelée périodiquement par l'orchestrateur pour alimenter `dernieresConstantes` (réponse (b) à Q4) | Un patient sans alerte doit aussi afficher ses constantes |
| D19 | 2026-11-17 | Contrat REST : OpenAPI 3.0.3 ; erreurs au format Problem Details (`application/problem+json`, natif Spring) ; identifiants générés `RDV-`, `CRN-`, `ORD-`, `AMB-` + 6 chiffres ; urgence = `POST /api/urgences` (réserve un créneau URGENCES, 409 `aucun-creneau` sinon) puis, en cas d'échec, `POST /api/ambulances` décidé par l'orchestrateur ; ordonnance = traitements d'un même médecin à la même date | La décision « RDV ou ambulance » reste dans l'orchestrateur (P5.2) ; formats cohérents avec D13 |
| D20 | 2026-11-17 | Créneaux générés par le service REST à partir d'un fichier `contracts/seed/plannings.json` (médecin, jours, heures, durée) à créer en P1.2 (réponse à Q5) | Respecte « données uniquement dans contracts/seed » sans lister chaque créneau à la main |
| D21 | 2026-11-17 | Build Java avec le **Maven Wrapper** (`mvnw`) inclus dans chaque projet ; pas d'installation globale de Maven | Maven n'est pas installé sur le poste ; le wrapper fixe la version pour tous |
| D22 | 2026-11-17 | Service REST : créneaux générés au démarrage depuis `plannings.json` sur **14 jours** (heure locale **Africa/Tunis**) ; une urgence réserve le premier créneau URGENCES **d'aujourd'hui qui n'a pas commencé** ; identifiants `CRN-`/`RDV-` séquentiels ; stockage en mémoire jusqu'à la phase 2 | Comportement déterministe et testable (horloge injectable) ; un appel le soir tombe sur 409 → ambulance (P5.2) |
| D23 | 2026-11-17 | Service GraphQL : schéma chargé directement depuis `contracts/graphql/` (copié par le pom) ; scalaires `Date`/`DateTime` codés sans dépendance, `DateTime` toujours écrit avec les secondes (`2026-11-17T10:30:00+01:00`) ; `enregistrerAlerte` livrée dès P1.3 (une fiche exige une alerte existante) ; identifiants de fiche `FIC-` + 6 chiffres ; erreurs GraphQL typées `NOT_FOUND` / `BAD_REQUEST` | Format de date identique au service REST ; `creerFicheUrgence` inutilisable sans alerte |
| D24 | 2026-11-17 | Service gRPC (Python) : seuil de phase 1 = fréquence cardiaque **> 140 bpm → CRITIQUE** (variable `TELEMETRIE_SEUIL_FC`, valeur de démonstration) ; identifiants d'alerte `ALR-` + 6 chiffres ; code gRPC généré depuis le `.proto` par `generer.py` (non versionné) ; mesure d'un patient absent du seed → `NOT_FOUND` | Valeur cohérente avec les exemples des contrats ; une seule source pour le code généré |

## Ports et routes

| Composant | Port interne | Route publique (via gateway :8080) |
|---|---|---|
| Service SOAP — Dossier & CNAM | 8081 | `/ws/dossier/**` |
| Service REST — Portail & RDV | 8082 | `/api/**` |
| Service GraphQL — Tableau de bord | 8083 | `/graphql` |
| Orchestrateur | 8084 | `/orchestrator/**`, `/events` (SSE) |
| Service gRPC — Télémétrie | 50051 | non exposé (consommé par l'orchestrateur) |
| Client Angular | 4200 | — |
| PostgreSQL | 5432 | non exposé |

## Conventions

- Identifiant patient commun : `TN-` + 7 chiffres (ex. `TN-0012345`).
- Namespace SOAP : `http://hopital.tn/dossier`.
- Packages Java : `tn.ginf.smarthealthcare.<service>` (`soap`, `rest`, `graphql`, `orchestrator`, `gateway`).
- Package Protobuf : `telemetrie`.
- Commits : `<identifiant de tâche>: <description courte>`.

## Versions des outils et librairies

À remplir au moment de la création de chaque projet, après vérification de la version réellement installée ou publiée. Ne rien écrire de mémoire.

| Outil / librairie | Version | Vérifiée le |
|---|---|---|
| Java (JDK) | 21.0.9 (installé) | 2026-11-17 |
| Spring Boot | 3.5.16 (dernière 3.x publiée ; la branche 4.x existe, non retenue : D2) | 2026-11-17 |
| Apache CXF | 4.1.9 (branche 4.1 = Spring 6.2 / Boot 3 ; la 4.2 cible Boot 4) | 2026-11-17 |
| Maven (via Maven Wrapper 3.3.4) | 3.9.16 | 2026-11-17 |
| Python | 3.12.4 (installé) | 2026-11-17 |
| grpcio / grpcio-tools | 1.85.1 (protobuf 7.36.2, libprotoc 35.1) | 2026-11-17 |
| Node.js / Angular CLI | | |
| PostgreSQL (image Docker) | | |
| zeep (outil de vérification du WSDL) | 4.3.3 | 2026-11-17 |
| graphql-core (outil de vérification du schéma GraphQL) | 3.3.2 | 2026-11-17 |
| openapi-spec-validator (outil de vérification OpenAPI) | 0.9.0 | 2026-11-17 |

## Questions ouvertes

| # | Question | Statut |
|---|---|---|
| Q1 | Qui émet le jeton JWT au login (endpoint dans la gateway, dans le service REST, ou un petit service d'authentification) ? | À décider avant P3.4 |
| Q3 | D2 prévoit Java 17, mais le JDK installé est 21.0.9 (vérifié le 2026-11-17) : garder 17 (installer un JDK 17) ou passer la cible à 21 ? | Réglée par D15 (Java 21) |
| Q4 | Comment alimenter `dernieresConstantes` du tableau de bord ? (a) uniquement avec les valeurs des alertes reçues ; (b) ajouter une 4ᵉ mutation `enregistrerConstantes` que l'orchestrateur appelle périodiquement | Réglée par D18 |
| Q5 | Les créneaux ne sont pas dans `contracts/seed/` : ajouter un fichier de plannings (médecin, jours, heures, durée) d'où le service REST génère ses créneaux ? | Réglée par D20 |
| Q2 | Rôles exacts côté client (patient, médecin, administrateur) et ce que chacun voit | À décider avant P4.1 |
