# Journal d'avancement

Une entrée par session de travail ou par tâche terminée, la plus récente en haut.

Modèle :

```
## AAAA-MM-JJ — <identifiants des tâches>
- Fait : …
- Vérification : <commande ou test exécuté> → <résultat observé>
- Reste à faire / blocages : …
- Contrats ou décisions modifiés : aucun | …
```

---

## 2026-11-17 — P1.3
- Fait : projet `services/graphql-dashboard` (Spring Boot 3.5.16, Spring for GraphQL, Java 21, Maven Wrapper), port 8083, `/graphql` + GraphiQL `/graphiql`. Schéma = `contracts/graphql/dashboard.graphqls` sans copie manuelle. Vue consolidée en mémoire initialisée depuis le seed (patients, médecins, services, traitements). Query `patient(id)` (profil, service, médecin traitant, traitements en cours, alertes récentes, fiches d'urgence ; `dernieresConstantes` vide jusqu'à `enregistrerConstantes`). Mutations `enregistrerAlerte` et `creerFicheUrgence` (D23).
- Vérification : `./mvnw test` → 6 tests, 0 échec (patient minimal, patient inconnu → null, alerte → fiche → visible dans le dossier, alerte inconnue → NOT_FOUND, patient inconnu → NOT_FOUND, DateTime invalide rejeté). Service lancé + requêtes HTTP : GraphiQL HTTP 200 ; `enregistrerAlerte` ALR-0001 → OK ; `creerFicheUrgence` → FIC-000001 ; `patient(id: "TN-0012345")` renvoie l'alerte CRITIQUE et la fiche liée à RDV-000001 ; alerte ALR-9999 → erreur `NOT_FOUND`. Inspection du schéma au démarrage : champs non encore implémentés = `couverture`, `historiqueSoins`, `patients`, `services`, `enregistrerConstantes`, `mettreAJourCouverture` (phase 2).
- Reste à faire / blocages : P1.4 (gRPC Python).
- Contrats ou décisions modifiés : aucun contrat ; D23.

## 2026-11-17 — P1.2
- Fait : `contracts/seed/plannings.json` (D20) + contrôle dans `check-seed.mjs` + README du seed. Projet `services/rest-portail` (Spring Boot 3.5.16, Java 21, Maven Wrapper) : `POST /api/urgences` → 201 + `Location` + RendezVous (créneau URGENCES de MED-004), 404 patient inconnu, 409 `/problemes/aucun-creneau`, 400 `/problemes/requete-invalide` (validation et JSON illisible) ; erreurs en `application/problem+json` ; horloge injectable (D22). Port 8082.
- Vérification : `node scripts/check-seed.mjs` → OK, 4 plannings. `./mvnw test` → 5 tests, 0 échec (10h10 → créneau 10h30 puis 11h00 ; 19h45 → 409 ; TN-9999999 → 404 ; TN-12 → 400 ; niveau GRAVE → 400). Service lancé (`java -jar`) + `curl` : TN-0012345 → HTTP 201, `Location: /api/rendez-vous/RDV-000001`, créneau 16h30 MED-004 ; réponse validée contre le schéma `RendezVous` du contrat OpenAPI → conforme ; TN-9999999 → HTTP 404 Problem Details.
- Reste à faire / blocages : P1.3 (GraphQL). Note : sous Windows, envoyer du JSON accentué à `curl` via un fichier UTF-8 (`--data-binary @fichier`).
- Contrats ou décisions modifiés : seed `plannings.json` (D20) ; D22.

## 2026-11-17 — P1.1
- Fait : projet `services/soap-dossier` (Spring Boot 3.5.16, Apache CXF 4.1.9, Java 21, Maven Wrapper). Classes JAX-WS générées par `cxf-codegen-plugin` depuis `contracts/soap/dossier.wsdl` (package `tn.ginf.smarthealthcare.soap.generated`, style bare via `src/main/jaxws/bindings.xml`). WSDL/XSD et seed copiés dans le classpath par le pom (source unique : `contracts/`). `DossierServiceImpl.verifierDroitsCNAM` : OUVERTS / EXPIRES / NON_AFFILIE selon `couvertures-cnam.json`, SOAP Fault `ErreurMetier` PATIENT_INCONNU ; validation des messages contre le XSD activée ; les 3 autres opérations lèvent « prévu en P2.SOAP.x ». Publication sur `/ws/dossier`, port 8081.
- Vérification : `./mvnw test` → 6 tests, 0 échec (OUVERTS 80 %, EXPIRES, NON_AFFILIE, fault PATIENT_INCONNU, `TN-12` rejeté par le schéma `cvc-pattern-valid`, `?wsdl` publié). `java -jar target/soap-dossier-0.1.0.jar` puis `curl` : `?wsdl` HTTP 200 ; requête de `contracts/soap/exemples` pour TN-0012345 → OUVERTS, VIG-2026-100001, 80 % ; TN-9999999 → HTTP 500 + Fault `PATIENT_INCONNU`.
- Reste à faire / blocages : test manuel dans SoapUI (vérification de phase 1) ; P1.2. En phase 3, le contexte de build Docker devra inclure `contracts/`. Pas de commit à la demande de l'utilisatrice tant que la phase 0 n'est pas poussée.
- Contrats ou décisions modifiés : aucun contrat ; versions Spring Boot, CXF, Maven notées.

## 2026-11-17 — P0.7 (fin de la phase 0)
- Fait : `contracts/rest/portail-openapi.yaml` (OpenAPI 3.0.3) : `GET /api/creneaux` (filtres médecin, service, date, disponibilité) ; CRUD `/api/rendez-vous` (GET liste, POST, GET, PUT, PATCH, DELETE) ; `GET/PATCH /api/patients/{patientId}` ; `GET /api/patients/{patientId}/ordonnances` ; `POST /api/urgences` et `POST /api/ambulances` ; réponses 200/201/204/400/404/409 en Problem Details (D19). Script `scripts/check_openapi.py`.
- Vérification : `python scripts/check_openapi.py` (openapi-spec-validator 0.9.0) → SPEC OK, 23 références résolues, 6/6 chemins avec leurs méthodes, 8 patients du seed conformes à `ProfilPatient`, 5/5 corps invalides REJETÉ (exit 0). Test négatif (référence vers un schéma inexistant) → SPEC KO (exit 1) ; ce cas n'était pas détecté par le validateur seul, d'où le contrôle explicite des `$ref`.
- Vérification de la phase 0 : WSDL importé dans SoapUI ✔, XSD valide les exemples ✔, `.proto` compilé ✔, schéma GraphQL chargé ✔, OpenAPI validé ✔.
- Reste à faire / blocages : Q5 (données de créneaux) avant P1.2 ; démarrer la phase 1 (P1.1).
- Contrats ou décisions modifiés : création de `portail-openapi.yaml` ; D19 ; Q5 ouverte.

## 2026-11-17 — Vérifications phase 0 (SoapUI) et D18
- Fait : import de `contracts/soap/dossier.wsdl` dans SoapUI Open Source par l'utilisatrice. Ajout de la mutation `enregistrerConstantes` (+ `ConstanteInput`) au contrat GraphQL et à l'exemple orchestrateur.
- Vérification : SoapUI (capture d'écran) → binding `DossierSoapBinding`, namespace `http://hopital.tn/dossier`, SOAP 1.1, style Document, parties `dossier.wsdl` + `dossier.xsd`, 4 opérations Literal avec leur soapAction, sans erreur. `python scripts/check_graphql.py` → SCHEMA OK (4 mutations), 4/4 exemples VALIDE, 4/4 négatifs REJETÉ (exit 0).
- Reste à faire / blocages : P0.7.
- Contrats ou décisions modifiés : `dashboard.graphqls` (D18) ; Q4 réglée.

## 2026-11-17 — P0.6
- Fait : `contracts/graphql/dashboard.graphqls` : scalaires `Date`/`DateTime` ; types Patient (profil, dernieresConstantes, alertesRecentes, traitementsEnCours, fichesUrgence, historiqueSoins agrégé), Medecin, ServiceHospitalier, Constante, Alerte, Traitement, Couverture, FicheUrgence, HistoriqueSoins ; queries `patient(id)`, `patients(filtres)`, `services` ; mutations orchestrateur `enregistrerAlerte`, `mettreAJourCouverture`, `creerFicheUrgence` ; énumérations alignées sur seed / XSD / proto. 4 fichiers d'exemple dans `contracts/graphql/exemples/`. Script `scripts/check_graphql.py`.
- Vérification : `python scripts/check_graphql.py` (graphql-core 3.3.2) → SCHEMA OK, 4/4 exemples VALIDE, 4/4 opérations négatives REJETÉ (champ inconnu, énumération invalide, argument manquant, mutation hors contrat) (exit 0).
- Reste à faire / blocages : Q4 (alimentation de `dernieresConstantes`) ; P0.7 (OpenAPI) ; import SoapUI du WSDL.
- Contrats ou décisions modifiés : création de `dashboard.graphqls` ; Q4 ouverte.

## 2026-11-17 — P0.5
- Fait : `contracts/grpc/telemetrie.proto` (proto3, package `telemetrie`) : service `Telemetrie` avec `EnvoyerMesures` (stream Mesure → RecapitulatifEnvoi) et `SuivreAlertes` (AbonnementAlertes → stream Alerte) ; énumérations `TypeMesure` (FREQUENCE_CARDIAQUE, SPO2, CHUTE) et `Niveau` (INFO, URGENT, CRITIQUE) ; horodatage `google.protobuf.Timestamp` ; options Java (D17). Script `scripts/check_proto.py`.
- Vérification : `python scripts/check_proto.py` (grpcio-tools 1.85.1, libprotoc 35.1) → compilation OK, EnvoyerMesures client streaming, SuivreAlertes server streaming, Mesure sérialisée/relue, stubs générés (exit 0). Test négatif (type inexistant dans une copie) → `"AlerteInexistante" is not defined` (exit 1). Génération Java non testée : le protoc de grpcio-tools ne contient pas le générateur Java ; elle sera vérifiée par le build de l'orchestrateur (P1.6).
- Reste à faire / blocages : P0.6 (schéma GraphQL) ; import SoapUI du WSDL (en cours côté utilisatrice).
- Contrats ou décisions modifiés : création de `telemetrie.proto` ; D17.

## 2026-11-17 — P0.4
- Fait : `contracts/soap/dossier.wsdl` (WSDL 1.1, SOAP 1.1, document/literal wrapped) important `dossier.xsd` ; 4 opérations `verifierDroitsCNAM`, `emettreFeuilleDeSoins`, `consulterHistorique`, `comparerGaranties` (D16), chacune avec le fault `ErreurMetier` ; adresse `http://localhost:8081/ws/dossier`. XSD complété (PriseEnCharge, Garantie, EcartMontant, Avantage, éléments `comparerGaranties`/`Response`) + 2 exemples. Script `scripts/check_wsdl.py`.
- Vérification : SoapUI non installé, remplacé par zeep 4.3.3 (client SOAP Python). `python scripts/check_wsdl.py contracts/soap/dossier.wsdl contracts/soap/dossier.xsd` → service DossierService/DossierPort chargé, 4 opérations, 4 enveloppes construites avec leur soapAction et corps conformes au XSD (exit 0). Test négatif (élément inexistant dans une copie du WSDL) → échec (exit 1). `java scripts/ValidateXsd.java ...` → 10/10 exemples VALIDE.
- Reste à faire / blocages : import dans SoapUI à faire quand l'outil sera installé (critère de la phase 0) ; P0.5 (`.proto`).
- Contrats ou décisions modifiés : `dossier.xsd` complété pour `comparerGaranties` ; D15 (Java 21), D16.

## 2026-11-17 — P0.3
- Fait : `contracts/soap/dossier.xsd` (namespace `http://hopital.tn/dossier`) : types simples IdentifiantPatient, IdentifiantMedecin, CodeVignette, NumeroFeuilleDeSoins, LettreCle, Coefficient, TauxRemboursement (0–100), Montant, Filiere, StatutDroits, TypeEvenement, CodeErreur ; types complexes Couverture, ActeMedical, FeuilleDeSoins, EntreeHistorique, Historique, ErreurMetier ; éléments wrapped pour `verifierDroitsCNAM`, `emettreFeuilleDeSoins`, `consulterHistorique` (+ `Response`) et `erreurMetier`. 8 messages d'exemple dans `contracts/soap/exemples/`. Validateur `scripts/ValidateXsd.java` (JDK seul). Hors P0.3 : `.gitattributes` (D14).
- Vérification : `java scripts/ValidateXsd.java contracts/soap/dossier.xsd contracts/soap/exemples/*.xml` → SCHEMA OK, 8/8 VALIDE (exit 0). Tests négatifs (identifiant `TN-12345`, lettre-clé `XX`, taux 120, feuille sans acte) → 4/4 INVALIDE (exit 1).
- Reste à faire / blocages : P0.4 (WSDL) ; les éléments de la comparaison des garanties seront ajoutés au XSD avec P0.4 (nom de l'opération à décider) ; Q3 (Java 17 ou 21).
- Contrats ou décisions modifiés : création de `contracts/soap/dossier.xsd` ; D14.

## 2026-11-17 — P0.2
- Fait : données fictives en JSON dans `contracts/seed/` (4 services, 4 médecins, 8 patients, 7 couvertures CNAM, 7 lettres-clés, 10 traitements) + `README.md` du dossier ; script `scripts/check-seed.mjs`. Cas de test prévus : vignette expirée (`TN-0067890`), patient sans couverture (`TN-0089012`).
- Vérification : `node scripts/check-seed.mjs` → `OK : 4 services, 4 médecins, 8 patients, 7 couvertures, 7 lettres-clés, 10 traitements` (exit 0) ; test négatif (identifiant et médecin faussés) → 5 erreurs détectées (exit 1), puis fichier restauré et contrôle de nouveau OK.
- Reste à faire / blocages : P0.3 (XSD).
- Contrats ou décisions modifiés : D13 (format JSON et formats d'identifiants des données fictives).

## 2026-11-17 — P0.1
- Fait : `git init -b main` ; arborescence créée avec `.gitkeep` : `contracts/{soap,grpc,graphql,rest,seed}`, `services/{soap-dossier,rest-portail,graphql-dashboard,grpc-telemetrie}`, `orchestrator/`, `gateway/`, `iot-simulator/`, `web-client/` (`docs/` existait déjà). PDF du sujet versionné. Pas de dépôt distant pour l'instant.
- Vérification : `git status` propre après le commit initial ; `git ls-files` liste les 13 dossiers attendus.
- Reste à faire / blocages : P0.2 (données fictives dans `contracts/seed/`).
- Contrats ou décisions modifiés : aucun

## 2026-11-17 — Initialisation
- Fait : création du dossier projet, de `ROADMAP.md`, `CLAUDE.md`, `docs/DECISIONS.md`, `docs/JOURNAL.md`, `docs/sujet/`, du script `scripts/roadmap-status.mjs` et des commandes Claude dans `.claude/`.
- Vérification : `node scripts/roadmap-status.mjs` affiche 0 tâche terminée et `P0.1` comme tâche suivante.
- Reste à faire : démarrer la phase 0 (P0.1).
- Contrats ou décisions modifiés : décisions D1 à D12 enregistrées.
