# Documentation technique — contrats des services

Approche *contract first* : chaque interface est décrite ici **avant** le code, et le code s'aligne sur ces fichiers
(`CLAUDE.md`). Les services chargent ou publient directement ces fichiers (aucune copie manuelle) ; les écarts sont
détectés par les tests et par `bash scripts/check-contrats.sh`.

Toutes les adresses publiques passent par la **gateway** (port 8080, 8090 sur le poste de développement, D43) ;
un jeton JWT (`POST /auth/login`, D44) est exigé sauf pour les outils de consultation marqués « public ».

## Contrats demandés par le sujet

| Protocole | Fichier | Service (port interne) | Consultation via la gateway |
|---|---|---|---|
| SOAP — **WSDL** | [`soap/dossier.wsdl`](soap/dossier.wsdl) + [`soap/dossier.xsd`](soap/dossier.xsd), exemples dans [`soap/exemples/`](soap/exemples/) ; voir [`soap/README.md`](soap/README.md) | Dossier patient & CNAM (8081) | `GET /ws/dossier?wsdl` (public) ; appels `POST /ws/dossier` |
| REST — **OpenAPI** 3.0.3 | [`rest/portail-openapi.yaml`](rest/portail-openapi.yaml) | Portail patient & rendez-vous (8082) | Swagger UI `GET /swagger-ui.html` (public, bouton « Authorize ») ; contrat servi tel quel `GET /openapi/portail-openapi.yaml` |
| GraphQL — **SDL** | [`graphql/dashboard.graphqls`](graphql/dashboard.graphqls), exemples dans [`graphql/exemples/`](graphql/exemples/) | Tableau de bord (8083) | GraphiQL `GET /graphiql` (public) ; requêtes `POST /graphql` |
| gRPC — **.proto** | [`grpc/telemetrie.proto`](grpc/telemetrie.proto) | Télémétrie (50051) | non exposé (le navigateur ne parle pas gRPC, D6) : consommé par les capteurs IoT et l'orchestrateur |

## Contrats complémentaires

| Interface | Fichier | Rôle |
|---|---|---|
| Flux SSE des alertes | [`sse/README.md`](sse/README.md) | `GET /events` : événements `alerte` et `prise-en-charge` (états TERMINE, AMBULANCE, ECHEC) poussés par l'orchestrateur au client web (D45, D53) |
| Trace des workflows (OpenAPI 3.0.3) | [`orchestrateur/workflows-openapi.yaml`](orchestrateur/workflows-openapi.yaml) | `GET /orchestrator/workflows` et `/{id}` : chaque instance de prise en charge, étape par étape (D54) |
| Données fictives | [`seed/README.md`](seed/README.md) | Seule source de données (patients, médecins, couvertures CNAM, lettres-clés, traitements, plannings) |

## Opérations par service

| Service | Opérations |
|---|---|
| SOAP | `verifierDroitsCNAM`, `emettreFeuilleDeSoins`, `consulterHistorique`, `comparerGaranties` ; erreurs = SOAP Fault `erreurMetier` (`PATIENT_INCONNU`, `VIGNETTE_INVALIDE`, `DONNEES_INVALIDES`) |
| REST | `GET /api/creneaux` ; CRUD `/api/rendez-vous` (GET, POST, GET/{id}, PUT, PATCH, DELETE) ; `GET/PATCH /api/patients/{id}` ; `GET /api/patients/{id}/ordonnances` ; `POST /api/urgences` ; `POST /api/ambulances` ; erreurs en Problem Details |
| GraphQL | Query `patient(id)`, `patients(filtres)`, `services` ; Mutation (orchestrateur seulement) `enregistrerConstantes`, `enregistrerAlerte`, `mettreAJourCouverture`, `creerFicheUrgence` |
| gRPC | `EnvoyerMesures` (flux client → serveur), `SuivreAlertes` et `SuivreMesures` (flux serveur → client) |

## Vérifications

| Contrôle | Commande | Ce qui est vérifié |
|---|---|---|
| Tous les contrats | `bash scripts/check-contrats.sh` | Lance les contrôles ci-dessous (outils Python : voir l'en-tête du script) |
| Seed | `node scripts/check-seed.mjs` | Formats d'identifiants, références croisées |
| XSD | `java scripts/ValidateXsd.java contracts/soap/dossier.xsd contracts/soap/exemples/*.xml` | Exemples SOAP valides contre le schéma |
| WSDL | `python scripts/check_wsdl.py contracts/soap/dossier.wsdl contracts/soap/dossier.xsd` | WSDL chargé par un client SOAP (zeep), 4 opérations, enveloppes conformes |
| .proto | `python scripts/check_proto.py` | Compilation, sens du streaming, énumérations, sérialisation |
| SDL | `python scripts/check_graphql.py` | Schéma chargé, exemples valides, requêtes hors contrat rejetées |
| OpenAPI (portail) | `python scripts/check_openapi.py` | Spécification valide, références, cohérence avec le seed |
| OpenAPI (trace) | inclus dans `check-contrats.sh` | Spécification valide |
| Code ↔ contrat | tests des services (`bash scripts/tests-unitaires.sh`) | REST : opérations du code = opérations du contrat, contrat servi tel quel ; SOAP : classes générées depuis le WSDL et messages validés contre le XSD ; GraphQL : schéma chargé depuis `contracts/` ; gRPC et orchestrateur : code généré depuis le `.proto` et le WSDL ; SSE : format exact des événements |
