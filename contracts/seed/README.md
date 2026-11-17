# Données fictives communes (`contracts/seed/`)

**Toutes les données de ce dossier sont fictives** : noms, téléphones, adresses, codes de vignette, valeurs des lettres-clés. Elles ne reprennent aucune personne ni aucun tarif réel.

C'est la seule source de données du projet : chaque service charge ici la partie qui le concerne, et aucun identifiant n'est inventé ailleurs (voir `CLAUDE.md`, règles anti-invention).

## Fichiers

| Fichier | Contenu | Utilisé par |
|---|---|---|
| `patients.json` | 8 patients : identité, coordonnées, service, pathologie, niveau de risque, médecin traitant | REST (profil), GraphQL (vue consolidée), gRPC / capteurs (identifiants), SOAP (dossier) |
| `medecins.json` | 4 médecins, un par service | REST (créneaux), GraphQL |
| `services-hospitaliers.json` | 4 services hospitaliers | GraphQL (filtres), REST |
| `couvertures-cnam.json` | Vignettes CNAM, filière, période de validité, taux de remboursement | SOAP |
| `lettres-cles.json` | Lettres-clés de cotation des actes et valeur unitaire (fictive) en dinars | SOAP (énumération `LettreCle` du XSD) |
| `traitements.json` | Traitements en cours et terminés | REST (ordonnances), GraphQL (traitement en cours) |

## Formats des identifiants

| Donnée | Format | Exemple |
|---|---|---|
| Patient | `TN-` + 7 chiffres | `TN-0012345` |
| Médecin | `MED-` + 3 chiffres | `MED-001` |
| Traitement | `TRT-` + 3 chiffres | `TRT-001` |
| Vignette CNAM | `VIG-` + année + `-` + 6 chiffres | `VIG-2026-100001` |
| Service hospitalier | code en majuscules | `CARDIO` |

Valeurs énumérées : `niveauRisque` ∈ `FAIBLE`, `MOYEN`, `ELEVE` ; `filiere` ∈ `PUBLIQUE`, `PRIVEE`, `REMBOURSEMENT` ; `statut` (traitement) ∈ `EN_COURS`, `TERMINE` ; `tauxRemboursement` ∈ 80, 50 (taux cités par le sujet).

## Lettres-clés

Un acte médical est coté par une lettre-clé et un coefficient (ex. `CS 1`, `K 20`). Montant de l'acte = coefficient × valeur unitaire de la lettre. Le remboursement CNAM applique ensuite le taux de la couverture. Les lettres reprennent les cotations usuelles ; les valeurs en dinars sont inventées.

## Cas particuliers prévus pour les tests

| Patient | Cas | Sert à |
|---|---|---|
| `TN-0067890` | Vignette expirée (fin 2025-12-31) | SOAP Fault « vignette invalide » (P2.SOAP.4) |
| `TN-0089012` | Aucune couverture CNAM | Prise en charge « à régulariser » (P5.1) |
| `TN-0012345` | Risque élevé, cardiaque, couvert à 80 % | Scénario principal d'alerte (phase 1) |
| `TN-0056789` | Antécédents de chute | Seuil « chute » (P2.GRPC.1) |

## Vérification

`node scripts/check-seed.mjs` : contrôle que chaque fichier est un JSON valide, le format des identifiants, les valeurs énumérées et les références entre fichiers.
