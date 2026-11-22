# Roadmap — Smart Healthcare (projet SOC, ING GINF 3)

Plateforme d'hôpital connecté et de suivi de santé à domicile : 4 services (SOAP, REST, GraphQL, gRPC), un orchestrateur, une API Gateway, un client web Angular, le tout conteneurisé avec Docker.

- Exigences officielles : [`docs/sujet/SUJET.md`](docs/sujet/SUJET.md)
- Décisions techniques : [`docs/DECISIONS.md`](docs/DECISIONS.md)
- Historique de l'avancement : [`docs/JOURNAL.md`](docs/JOURNAL.md)
- État rapide : `node scripts/roadmap-status.mjs`

## Légende

| Marque | Sens |
|---|---|
| `- [ ]` | À faire |
| `- [~]` | En cours |
| `- [x]` | Terminé **et vérifié** (le critère « Vérification » de la phase est satisfait) |

Règles :
- On avance phase par phase, dans l'ordre. Une phase commence quand la précédente est entièrement cochée, sauf accord explicite noté dans le journal.
- Une tâche n'est cochée `[x]` qu'après exécution réelle de sa vérification.
- Les identifiants (`P0.1`, `P2.REST.3`…) ne changent jamais : le journal et les commits y font référence.

---

## Phase 0 — Cadrage et contrats (contract first)

Objectif : figer le domaine et les 4 contrats avant d'écrire le code des services.

- [x] P0.1 Initialiser le dépôt git et l'arborescence (`contracts/`, `services/`, `orchestrator/`, `gateway/`, `iot-simulator/`, `web-client/`, `docs/`)
- [x] P0.2 Données fictives communes dans `contracts/seed/` : patients (identifiant commun `TN-xxxxxxx`), médecins, services hospitaliers, codes de vignette CNAM, traitements
- [x] P0.3 `contracts/soap/dossier.xsd` : types IdentifiantPatient (pattern), LettreCle (énumération), TauxRemboursement (0–100), ActeMedical, FeuilleDeSoins, Couverture, Historique
- [x] P0.4 `contracts/soap/dossier.wsdl` (style document/literal) : opérations `verifierDroitsCNAM`, `emettreFeuilleDeSoins`, `consulterHistorique`, comparaison des garanties entre deux prises en charge
- [x] P0.5 `contracts/grpc/telemetrie.proto` : `EnvoyerMesures` (client streaming), `SuivreAlertes` (server streaming), messages Mesure / Alerte / Niveau
- [x] P0.6 `contracts/graphql/dashboard.graphqls` : query `patient(id)`, query `patients(filtres)`, mutations alimentées par l'orchestrateur (`enregistrerAlerte`, `mettreAJourCouverture`, `creerFicheUrgence`)
- [x] P0.7 `contracts/rest/portail-openapi.yaml` : créneaux, rendez-vous (CRUD), ordonnances, profil patient, urgences

**Vérification phase 0** : le WSDL s'importe sans erreur dans SoapUI ; le XSD valide un message d'exemple ; `protoc` compile le `.proto` ; le schéma GraphQL se charge sans erreur ; le fichier OpenAPI passe un validateur OpenAPI 3.

---

## Phase 1 — Squelette de bout en bout (une opération par service)

Objectif : le workflow complet fonctionne en version minimale avant d'enrichir quoi que ce soit.

- [x] P1.1 Service SOAP : `verifierDroitsCNAM` seul, généré depuis le WSDL (JAX-WS / Apache CXF, approche top-down)
- [x] P1.2 Service REST : `POST /api/urgences` seul
- [x] P1.3 Service GraphQL : mutation `creerFicheUrgence` + query `patient(id)` minimale
- [x] P1.4 Service gRPC : `EnvoyerMesures` + `SuivreAlertes` avec un seul seuil (fréquence cardiaque)
- [x] P1.5 Script capteurs IoT minimal (un patient, injection d'une valeur critique)
- [x] P1.6 Orchestrateur : abonnement au flux d'alertes gRPC → appel SOAP → appel REST → mutation GraphQL, avec logs de chaque étape

**Vérification phase 1** : services lancés en local, une valeur critique envoyée par le script produit une fiche d'urgence lisible par la query GraphQL `patient(id)`. Chaque service testé seul (SoapUI, Swagger UI, GraphiQL, client gRPC).

---

## Phase 2 — Compléter les services

### SOAP — Dossier patient & facturation CNAM
- [x] P2.SOAP.1 `emettreFeuilleDeSoins` (enregistrement des actes cotés en lettres-clés)
- [x] P2.SOAP.2 `consulterHistorique` (historique médical certifié)
- [x] P2.SOAP.3 Comparaison des garanties entre deux prises en charge
- [x] P2.SOAP.4 Erreurs métier renvoyées en SOAP Fault (patient inconnu, vignette invalide)
- [x] P2.SOAP.5 Persistance PostgreSQL + chargement des données de `contracts/seed/`

### REST — Portail patient & rendez-vous
- [x] P2.REST.1 `GET` des créneaux disponibles (filtres médecin / date)
- [x] P2.REST.2 CRUD complet des rendez-vous (`POST`, `GET`, `PUT`, `PATCH`, `DELETE`)
- [x] P2.REST.3 Historique des prescriptions et ordonnances
- [x] P2.REST.4 Mise à jour du profil et des coordonnées (`PATCH`)
- [x] P2.REST.5 Codes HTTP corrects (201, 204, 400, 404, 409) et validation des entrées
- [x] P2.REST.6 Documentation OpenAPI exposée (Swagger UI) et alignée sur `contracts/rest/`
- [x] P2.REST.7 Persistance PostgreSQL du portail (créneaux, rendez-vous, profils, ordonnances) — ajoutée le 2026-11-21 à la demande de l'utilisatrice (D8)

### GraphQL — Tableau de bord médical
- [x] P2.GQL.1 Extraction personnalisée d'un dossier (profil, dernières constantes, alertes récentes, traitement en cours) en une requête
- [x] P2.GQL.2 Filtrage dynamique des patients par service hospitalier, pathologie, niveau de risque
- [x] P2.GQL.3 Agrégation à la demande de l'historique de soins
- [x] P2.GQL.4 Persistance de la vue consolidée (PostgreSQL) + données initiales

### gRPC — Télémétrie & alertes temps réel
- [x] P2.GRPC.1 Seuils critiques : fréquence cardiaque haute/basse, SpO2, chute
- [x] P2.GRPC.2 Niveaux d'alerte (INFO / URGENT / CRITIQUE) et priorisation
- [x] P2.GRPC.3 Persistance des mesures
- [x] P2.GRPC.4 Script capteurs multi-patients, fréquence et scénarios configurables
- [x] P2.GRPC.5 Flux des mesures `SuivreMesures` (ajout au contrat `.proto`) relayé par l'orchestrateur vers `enregistrerConstantes` — ajoutée le 2026-11-21 (Q7)

**Vérification phase 2** : chaque opération listée ci-dessus est testée (collection SoapUI, collection Postman/Swagger, requêtes GraphiQL, client gRPC) et les tests automatisés du service passent.

---

## Phase 3 — API Gateway et Docker

- [x] P3.1 Dockerfile pour chaque microservice (SOAP, REST, GraphQL, gRPC, orchestrateur, gateway) + script IoT + client web
- [x] P3.2 `docker-compose.yml` : tous les services + un conteneur PostgreSQL avec une base par service
- [x] P3.3 API Gateway (Spring Cloud Gateway) : routes vers chaque service (voir `docs/DECISIONS.md`)
- [x] P3.4 Authentification JWT vérifiée à la gateway (emplacement de l'émission du jeton : voir question ouverte dans `docs/DECISIONS.md`)
- [x] P3.5 Diffusion des alertes vers le client (SSE) exposée par l'orchestrateur à travers la gateway

**Vérification phase 3** : `docker compose up` démarre tout ; le workflow de la phase 1 fonctionne en passant uniquement par la gateway (port 8080).

---

## Phase 4 — Client web Angular

- [x] P4.1 Projet Angular, routing, authentification (stockage du JWT, intercepteur HTTP)
- [x] P4.2 Espace patient : créneaux et prise / modification / annulation de rendez-vous (REST)
- [x] P4.3 Espace patient : ordonnances et profil (REST)
- [x] P4.4 Tableau de bord médecin : liste filtrable des patients et fiche patient (GraphQL)
- [ ] P4.5 Alertes en direct et fiche d'urgence affichée sans rechargement (SSE)
- [ ] P4.6 Vue administrateur : consultation SOAP via la gateway (droits CNAM, historique)

**Vérification phase 4** : chaque écran fonctionne contre la stack Docker ; une alerte simulée apparaît en direct sur le tableau de bord.

---

## Phase 5 — Robustesse, tests et documentation

- [ ] P5.1 Orchestrateur : patient non couvert → prise en charge « à régulariser »
- [ ] P5.2 Orchestrateur : aucun créneau libre → envoi d'ambulance
- [ ] P5.3 Orchestrateur : délais d'attente, nouvelles tentatives et trace de chaque instance de workflow
- [ ] P5.4 Tests unitaires par service + test d'intégration du scénario d'alerte complet
- [ ] P5.5 Documentation technique : WSDL, OpenAPI, SDL, `.proto` à jour dans `contracts/`
- [ ] P5.6 Schémas : architecture microservices, BPMN du workflow, déploiement Docker
- [ ] P5.7 Scénario de démonstration écrit pas à pas
- [ ] P5.8 Rapport final

**Vérification phase 5** : tous les tests passent ; le scénario de démo se déroule sans intervention manuelle sur le code ; la documentation correspond au code livré.

---

## Phase 6 — Bonus (uniquement après les phases 0 à 5)

Le sujet autorise à ajouter des services, complexifier l'orchestration ou enrichir le client. Ordre de priorité convenu :

- [ ] P6.1 Service de notifications asynchrone (RabbitMQ) : l'orchestrateur publie, le service notifie médecin et famille (simulé)
- [ ] P6.2 Annuaire de services (Eureka) : découverte dynamique par la gateway
- [ ] P6.3 Orchestration enrichie : compensations, escalade si l'alerte n'est pas acquittée
- [ ] P6.4 Service supplémentaire « Pharmacie » (stock, délivrance d'ordonnances)
- [ ] P6.5 Client mobile (Ionic, réutilisation du code Angular)
