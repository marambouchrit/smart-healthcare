# Contrat SOAP — Dossier patient & facturation CNAM

| Fichier | Rôle |
|---|---|
| `dossier.xsd` | Types et éléments des messages (namespace `http://hopital.tn/dossier`) |
| `dossier.wsdl` | Opérations, liaison SOAP 1.1 document/literal, adresse (importe `dossier.xsd`) |
| `exemples/` | Messages d'exemple, tous valides contre le XSD (`java scripts/ValidateXsd.java contracts/soap/dossier.xsd contracts/soap/exemples/*.xml`) |

Adresse : `http://localhost:8081/ws/dossier` (WSDL : `?wsdl`) ; via la gateway : `/ws/dossier`.

## Opérations

| Opération | Entrée | Sortie | Règles (docs/DECISIONS.md) |
|---|---|---|---|
| `verifierDroitsCNAM` | patient, lettre-clé, date de prestation | statut `OUVERTS` / `EXPIRES` / `NON_AFFILIE`, prise en charge, couverture, motif | Droits ouverts si la date est dans la période de la vignette |
| `emettreFeuilleDeSoins` | patient, vignette, actes (lettre-clé, coefficient, date, médecin) | feuille numérotée `FS-AAAA-xxxxxx`, montants, taux, remboursé, certification `SHA256:…` | D26 : montant = coefficient × valeur de la lettre-clé ; remboursé = total × taux |
| `consulterHistorique` | patient, période optionnelle | entrées `CONSULTATION` (prescriptions) et `ACTE` (actes facturés, avec numéro de feuille), date de certification | D27 |
| `comparerGaranties` | deux prises en charge (patient, lettre-clé, coefficient, date) | garantie de chacune, écart des remboursements, plus avantageuse `A` / `B` / `EGALE` | D16 : taux 0 si droits non ouverts |

## Erreurs (SOAP Fault)

Les erreurs métier sont des SOAP Faults (HTTP 500, `faultcode` `soap:Server`) dont le `detail` contient l'élément `erreurMetier` du contrat :

```xml
<soap:Fault>
  <faultcode>soap:Server</faultcode>
  <faultstring>…message…</faultstring>
  <detail><erreurMetier xmlns="http://hopital.tn/dossier"><code>VIGNETTE_INVALIDE</code><message>…</message></erreurMetier></detail>
</soap:Fault>
```

| Code | Cas | Opérations |
|---|---|---|
| `PATIENT_INCONNU` | Identifiant absent du dossier | toutes |
| `VIGNETTE_INVALIDE` | Vignette d'un autre patient, patient non affilié, ou acte en dehors de la période de la vignette | `emettreFeuilleDeSoins` |
| `DONNEES_INVALIDES` | Médecin inconnu, lettre-clé sans valeur, période de recherche inversée | `emettreFeuilleDeSoins`, `consulterHistorique` |

Un message **non conforme au XSD** (ex. identifiant `TN-12`) est rejeté avant tout traitement par une Fault `soap:Client` dont le message commence par `cvc-` (validation de schéma activée sur le service).

`verifierDroitsCNAM` et `comparerGaranties` ne lèvent pas de Fault pour une vignette expirée : c'est une réponse normale (statut `EXPIRES`, taux 0).
