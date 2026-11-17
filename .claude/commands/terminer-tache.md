---
description: Vérifie une tâche, la coche si la vérification réussit et met à jour le journal
argument-hint: "[identifiant de tâche, ex. P1.2]"
---
Termine la tâche $ARGUMENTS (si vide, la tâche `[~]` en cours dans `ROADMAP.md`).

1. Exécute réellement la vérification prévue (critère « Vérification » de la phase, tests du service, commande ou requête adaptée). Montre la commande et sa sortie.
2. Si la vérification échoue ou ne peut pas être exécutée : laisse la tâche en `[~]`, explique ce qui manque, et arrête-toi.
3. Si elle réussit :
   - coche `[x]` la tâche dans `ROADMAP.md` ;
   - ajoute en haut de `docs/JOURNAL.md` une entrée selon le modèle (date du jour, tâche, fait, vérification avec le résultat observé, reste à faire, contrats ou décisions modifiés) ;
   - si une version de librairie a été fixée, renseigne-la dans `docs/DECISIONS.md` ;
   - si une nouvelle commande utile existe (build, tests), ajoute-la au tableau « Commandes du projet » de `CLAUDE.md`.
4. Propose le message de commit `<identifiant>: <description courte>`.
5. Exécute `node scripts/roadmap-status.mjs` et annonce la tâche suivante.
