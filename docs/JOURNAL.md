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
