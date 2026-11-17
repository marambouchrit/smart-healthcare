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
