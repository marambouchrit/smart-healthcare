---
description: Prépare et démarre la prochaine tâche de la roadmap
argument-hint: "[identifiant de tâche optionnel, ex. P0.3]"
---
Démarre la prochaine tâche du projet Smart Healthcare.

Tâche demandée : $ARGUMENTS (si vide, prends la première tâche `[~]`, sinon la première `[ ]` de `ROADMAP.md` dans l'ordre).

1. Exécute `node scripts/roadmap-status.mjs`.
2. Si la tâche demandée appartient à une phase plus avancée que la phase active, ou à la phase 6 alors que les phases 0 à 5 ne sont pas terminées, arrête-toi et demande confirmation.
3. Relis dans `docs/sujet/SUJET.md` le passage qui justifie cette tâche, et dans `docs/DECISIONS.md` les décisions qui la contraignent. Cite-les brièvement.
4. Présente un plan court : fichiers à créer ou modifier, contrat de `contracts/` concerné, et la vérification qui permettra de cocher la tâche.
5. Si une information manque (version, choix non décidé, donnée absente de `contracts/seed/`), pose la question au lieu de supposer.
6. Après accord, passe la tâche à `[~]` dans `ROADMAP.md` et commence.
