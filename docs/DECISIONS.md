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
| Spring Boot | | |
| Apache CXF | | |
| Python | | |
| grpcio / grpcio-tools | 1.85.1 (protobuf 7.36.2, libprotoc 35.1) | 2026-11-17 |
| Node.js / Angular CLI | | |
| PostgreSQL (image Docker) | | |
| zeep (outil de vérification du WSDL) | 4.3.3 | 2026-11-17 |

## Questions ouvertes

| # | Question | Statut |
|---|---|---|
| Q1 | Qui émet le jeton JWT au login (endpoint dans la gateway, dans le service REST, ou un petit service d'authentification) ? | À décider avant P3.4 |
| Q3 | D2 prévoit Java 17, mais le JDK installé est 21.0.9 (vérifié le 2026-11-17) : garder 17 (installer un JDK 17) ou passer la cible à 21 ? | Réglée par D15 (Java 21) |
| Q2 | Rôles exacts côté client (patient, médecin, administrateur) et ce que chacun voit | À décider avant P4.1 |
