# Architecture microservices

Une seule porte d'entrée (la gateway), quatre services qui ne s'appellent jamais entre eux, et un orchestrateur qui est
le seul à enchaîner les appels (couplage lâche, D5). Chaque service a sa propre base dans le même conteneur PostgreSQL.

```mermaid
flowchart LR
    subgraph Utilisateurs
        NAV["Navigateur<br/>client Angular<br/>(patient, médecin, admin)"]
        OUTILS["Outils de test<br/>SoapUI, Swagger UI, GraphiQL"]
    end
    CAPT["Capteurs IoT virtuels<br/>(Python)"]

    subgraph Plateforme["Plateforme Smart Healthcare"]
        GW["API Gateway<br/>Spring Cloud Gateway :8080<br/>JWT, routes"]
        WEB["Client web<br/>nginx :4200"]
        SOAP["Dossier & CNAM<br/>SOAP / JAX-WS :8081"]
        REST["Portail & RDV<br/>REST / Spring MVC :8082"]
        GQL["Tableau de bord<br/>GraphQL :8083"]
        ORCH["Orchestrateur<br/>workflow + SSE :8084"]
        GRPC["Télémétrie<br/>gRPC / Python :50051"]
        subgraph PG["PostgreSQL :5432 (1 conteneur, 1 base par service)"]
            DB1[("soap_dossier")]
            DB2[("rest_portail")]
            DB3[("graphql_dashboard")]
            DB4[("grpc_telemetrie")]
            DB5[("orchestrator")]
        end
    end

    NAV -- "HTTP + JWT<br/>REST, GraphQL, SOAP, SSE" --> GW
    OUTILS -- "HTTP" --> GW
    GW -- "/, /patient/**, /medecin/**, /admin/**" --> WEB
    GW -- "/ws/dossier" --> SOAP
    GW -- "/api/**" --> REST
    GW -- "/graphql" --> GQL
    GW -- "/events, /orchestrator/**" --> ORCH

    CAPT -- "gRPC EnvoyerMesures<br/>(flux client)" --> GRPC
    GRPC -- "gRPC SuivreAlertes, SuivreMesures<br/>(flux serveur)" --> ORCH
    ORCH -- "SOAP verifierDroitsCNAM" --> SOAP
    ORCH -- "REST /api/urgences, /api/ambulances" --> REST
    ORCH -- "GraphQL mutations" --> GQL

    SOAP --- DB1
    REST --- DB2
    GQL --- DB3
    GRPC --- DB4
    ORCH --- DB5
```

| Composant | Techno | Rôle | Contrat |
|---|---|---|---|
| API Gateway | Spring Cloud Gateway (WebFlux) | Seul port publié ; émet et vérifie le JWT ; routes vers les services et le client | — (D43, D44, D51) |
| Client web | Angular 21 servi par nginx | Écrans patient, médecin (alertes en direct), administrateur | — (D48–D50) |
| Dossier & CNAM | Spring Boot + Apache CXF | Droits CNAM, feuilles de soins, historique certifié, comparaison de garanties | WSDL + XSD |
| Portail & RDV | Spring Boot + Spring MVC + JPA | Créneaux, rendez-vous, profil, ordonnances, urgences, ambulances | OpenAPI |
| Tableau de bord | Spring for GraphQL + JDBC | Vue consolidée des patients : constantes, alertes, couverture, fiches | SDL |
| Télémétrie | Python + grpcio | Reçoit les mesures, détecte et diffuse les alertes | `.proto` |
| Orchestrateur | Spring Boot | Workflow de prise en charge, relais des constantes, flux SSE, trace | SSE, OpenAPI de la trace |
| Capteurs IoT | Python | Scénarios de mesures (normal, alertes, afflux) | `.proto` |

Règle d'architecture : aucun service ne lit la base d'un autre ni ne l'appelle ; seul l'orchestrateur relie
SOAP, REST, GraphQL et gRPC.
