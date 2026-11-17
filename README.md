# Smart Healthcare

Plateforme interopérable d'hôpital connecté et de suivi de santé à domicile — projet individuel du module Service Oriented Computing (ING GINF 3, 2026-2027).

| Service | Protocole | Rôle |
|---|---|---|
| Dossier patient & facturation CNAM | SOAP | Droits d'assurance, feuilles de soins, historique certifié |
| Portail patient & rendez-vous | REST | Créneaux, rendez-vous, ordonnances, profil |
| Tableau de bord médical | GraphQL | Vue consolidée et filtrable pour les soignants |
| Télémétrie & alertes | gRPC | Streaming des capteurs, détection de seuils critiques |

Un orchestrateur enchaîne ces services pour le workflow « alerte médicale à domicile » ; une API Gateway expose le tout à un client web Angular. Chaque service tourne dans son conteneur Docker.

- Plan et avancement : [`ROADMAP.md`](ROADMAP.md) — état rapide : `node scripts/roadmap-status.mjs`
- Exigences : [`docs/sujet/SUJET.md`](docs/sujet/SUJET.md)
- Décisions techniques : [`docs/DECISIONS.md`](docs/DECISIONS.md)
- Journal : [`docs/JOURNAL.md`](docs/JOURNAL.md)
- Règles pour l'assistant IA : [`CLAUDE.md`](CLAUDE.md)
