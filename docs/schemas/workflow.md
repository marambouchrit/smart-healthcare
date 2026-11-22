# Workflow de prise en charge d'une alerte

Le processus métier est modélisé en **BPMN 2.0** dans [`workflow-prise-en-charge.bpmn`](workflow-prise-en-charge.bpmn)
(à ouvrir dans Camunda Modeler ou sur https://demo.bpmn.io). Il est exécuté par le code de l'orchestrateur
(`WorkflowAlerte`, orchestration codée, D9), et chaque instance est tracée (`GET /orchestrator/workflows`, D54).

Le diagramme de séquence ci-dessous montre le même scénario, message par message.

```mermaid
sequenceDiagram
    autonumber
    participant C as Capteur IoT
    participant T as Télémétrie (gRPC)
    participant O as Orchestrateur
    participant S as Dossier & CNAM (SOAP)
    participant R as Portail (REST)
    participant G as Tableau de bord (GraphQL)
    participant W as Client web (SSE)

    C->>T: EnvoyerMesures (FC 165 bpm)
    T-->>O: SuivreAlertes : alerte CRITIQUE (seuil 140 bpm)
    O-->>W: SSE « alerte »
    O->>S: verifierDroitsCNAM (patient, lettre-clé C, date)
    S-->>O: OUVERTS / EXPIRES / NON_AFFILIE
    Note over O: droits non ouverts → « à régulariser »<br/>(les soins ne sont jamais bloqués, D52)
    O->>R: POST /api/urgences
    alt créneau d'urgence libre
        R-->>O: 201 rendez-vous RDV-…
    else aucun créneau (409)
        R-->>O: 409 aucun-creneau
        O->>R: POST /api/ambulances
        R-->>O: 201 intervention AMB-…, arrivée estimée
    end
    O->>G: enregistrerAlerte, mettreAJourCouverture
    O->>G: creerFicheUrgence (CONSULTATION_URGENCE ou AMBULANCE)
    G-->>O: fiche FIC-…
    O-->>W: SSE « prise-en-charge » (TERMINE / AMBULANCE)
    Note over O,G: chaque appel : délais 2 s / 5 s, 3 essais sûrs,<br/>étape en échec → ECHEC tracé et diffusé (D54)
```

| Étape (trace) | Service | Opération | Nouvel essai si panne |
|---|---|---|---|
| ALERTE_RECUE | gRPC | `SuivreAlertes` (niveau ≥ URGENT) | — (reconnexion toutes les 5 s) |
| DROITS_CNAM | SOAP | `verifierDroitsCNAM` | oui (lecture) |
| CRENEAU_URGENCE | REST | `POST /api/urgences` (REFUS = 409) | seulement si la requête n'est pas partie |
| AMBULANCE | REST | `POST /api/ambulances` | seulement si la requête n'est pas partie |
| ALERTE_TABLEAU_DE_BORD | GraphQL | `enregistrerAlerte` | oui (idempotent) |
| COUVERTURE | GraphQL | `mettreAJourCouverture` | oui (idempotent) |
| FICHE_URGENCE | GraphQL | `creerFicheUrgence` | seulement si la requête n'est pas partie |
