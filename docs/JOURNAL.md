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
