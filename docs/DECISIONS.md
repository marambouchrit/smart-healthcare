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
| D25 | 2026-11-17 | Orchestrateur : workflow indépendant des protocoles (interfaces `Ports`, testables sans réseau) ; abonnement gRPC `SuivreAlertes` au niveau minimal **URGENT**, reconnexion automatique toutes les 5 s ; droits CNAM vérifiés pour la lettre-clé **C** à la date locale (Tunis) de l'alerte ; droits non ouverts → `aRegulariser` transmis au REST et fiche `A_REGULARISER` ; HTTP 409 → workflow arrêté et journalisé (ambulance en P5.2) ; clients générés : SOAP depuis le WSDL (CXF), gRPC depuis le `.proto` (protobuf-maven-plugin) | Ordre de démarrage des services sans importance ; robustesse complète en phase 5 |
| D26 | 2026-11-21 | `emettreFeuilleDeSoins` : montant d'un acte = coefficient × valeur de la lettre-clé (`lettres-cles.json`), remboursé = total × taux de la couverture, arrondis au millime ; la vignette doit appartenir au patient et couvrir la date de **chaque** acte, sinon SOAP Fault **`VIGNETTE_INVALIDE`** (réponse (a) à Q6) ; médecin inconnu → `DONNEES_INVALIDES` ; numéro `FS-<année d'émission>-<6 chiffres>` ; certification simulée = empreinte SHA-256 du contenu de la feuille | Comportement d'un vrai système d'assurance ; empreinte vérifiable plutôt qu'une valeur arbitraire |
| D27 | 2026-11-21 | `consulterHistorique` (choix proposé par Claude, validé par délégation de l'utilisatrice) : entrées `CONSULTATION` = prescriptions de `traitements.json` regroupées par (médecin, date) ; entrées `ACTE` = un acte de chaque feuille de soins émise, avec son numéro de feuille ; tri chronologique ; filtre `dateDebut`/`dateFin` inclusif (début > fin → `DONNEES_INVALIDES`) ; `dateCertification` = instant de la consultation | Seules sources disponibles sans inventer de données ; le lien acte → feuille certifiée justifie le mot « certifié » |
| D28 | 2026-11-21 | Règles des rendez-vous REST : un créneau porte au plus un rendez-vous confirmé ; un créneau déjà commencé ne se réserve plus ; PUT = remplacement complet, le patient ne change pas (400), changer de créneau libère l'ancien ; PATCH (motif et/ou statut, au moins un champ) ; transitions CONFIRME → ANNULE (libère le créneau) ou HONORE ; un rendez-vous ANNULE n'est plus modifiable (409 `rendez-vous-annule`), un HONORE ne s'annule plus (409 `transition-interdite`) ; DELETE libère le créneau ; 409 `creneau-indisponible` | Comportement cohérent d'un agenda ; codes 409 distincts et explicites pour le client |
| D29 | 2026-11-21 | Swagger UI du service REST affiche **le contrat** (`contracts/rest/portail-openapi.yaml`, copié par le pom et servi sur `/openapi/portail-openapi.yaml`) et non une description régénérée ; la description générée par springdoc (`/v3/api-docs`) sert uniquement au test d'alignement (mêmes chemins, méthodes et operationId) | Une seule documentation, celle du contrat (contract first) ; l'alignement code ↔ contrat est vérifié automatiquement |
| D30 | 2026-11-21 | `patients(filtres)` : critères combinés par ET ; `pathologie` = recherche partielle sans casse ni accents ; `niveauRisque` = l'un des niveaux de la liste (liste vide = pas de filtre) ; service inconnu → liste vide ; tri par risque décroissant puis par nom ; `services` triés par libellé | Usage d'un tableau de bord médical : les patients les plus à risque d'abord |

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
| grpc-java (orchestrateur) | 1.85.1 ; protobuf-java 4.36.2 (même protobuf 36.2 que Python) | 2026-11-17 |
| protobuf-maven-plugin (org.xolstice) / os-maven-plugin | 0.6.1 / 1.7.1 | 2026-11-17 |
| zeep (outil de vérification du WSDL) | 4.3.3 | 2026-11-17 |
| graphql-core (outil de vérification du schéma GraphQL) | 3.3.2 | 2026-11-17 |
| springdoc-openapi-starter-webmvc-ui (service REST) | 2.10.1 (cible Spring Boot 3.5.16) | 2026-11-21 |
| openapi-spec-validator (outil de vérification OpenAPI) | 0.9.0 | 2026-11-17 |

## Questions ouvertes

| # | Question | Statut |
|---|---|---|
| Q1 | Qui émet le jeton JWT au login (endpoint dans la gateway, dans le service REST, ou un petit service d'authentification) ? | À décider avant P3.4 |
| Q3 | D2 prévoit Java 17, mais le JDK installé est 21.0.9 (vérifié le 2026-11-17) : garder 17 (installer un JDK 17) ou passer la cible à 21 ? | Réglée par D15 (Java 21) |
| Q4 | Comment alimenter `dernieresConstantes` du tableau de bord ? (a) uniquement avec les valeurs des alertes reçues ; (b) ajouter une 4ᵉ mutation `enregistrerConstantes` que l'orchestrateur appelle périodiquement | Réglée par D18 |
| Q5 | Les créneaux ne sont pas dans `contracts/seed/` : ajouter un fichier de plannings (médecin, jours, heures, durée) d'où le service REST génère ses créneaux ? | Réglée par D20 |
| Q6 | `emettreFeuilleDeSoins` : montant d'un acte = coefficient × valeur de la lettre-clé, remboursé = total × taux (proposé). Si la vignette est expirée : refuser (SOAP Fault `VIGNETTE_INVALIDE`) ou émettre la feuille à 0 % ? | Réglée par D26 (refus `VIGNETTE_INVALIDE`) |
| Q7 | Qui alimente `enregistrerConstantes` (D18) ? Le contrat gRPC ne diffuse que les alertes : l'orchestrateur ne voit pas les mesures normales. Proposition : ajouter au `.proto` un RPC `SuivreMesures` (server streaming) que l'orchestrateur relaie périodiquement vers `enregistrerConstantes` (changement de contrat à valider) | À décider (la mutation GraphQL existe et fonctionne) |
| Q2 | Rôles exacts côté client (patient, médecin, administrateur) et ce que chacun voit | À décider avant P4.1 |
