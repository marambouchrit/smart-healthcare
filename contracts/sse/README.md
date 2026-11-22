# Contrat du flux SSE `/events` — alertes en direct (P3.5, D6, D45)

Le navigateur ne parle pas gRPC (D6) : l'orchestrateur relaie au client web, en **Server-Sent Events**, les alertes
qu'il reçoit du service de télémétrie et le résultat du workflow de prise en charge.

## Accès

| | |
|---|---|
| URL publique | `GET http://<gateway>/events` (gateway, port 8080 ; 8090 sur le poste de développement, D43) |
| Service | orchestrateur, port 8084 (non publié) |
| Réponse | `200`, `Content-Type: text/event-stream;charset=UTF-8` |
| Authentification | jeton JWT de `POST /auth/login` : en-tête `Authorization: Bearer <jeton>` **ou** `?access_token=<jeton>` (seul chemin où le paramètre est accepté, car `EventSource` ne peut pas envoyer d'en-tête — D44) |
| Sans jeton / jeton invalide | `401` (gateway) |

Le flux ne rejoue pas le passé : un abonné reçoit les événements survenus **après** sa connexion. L'historique se lit
dans le tableau de bord GraphQL. La connexion est fermée par l'orchestrateur au bout de 30 minutes ; `EventSource`
se reconnecte alors tout seul.

## Événements

Chaque événement a un nom (`event:`), un identifiant (`id:`) et une donnée JSON sur une ligne (`data:`).
Dates : ISO 8601 avec décalage, à la seconde, fuseau Africa/Tunis (comme les autres services, D23 / D39).

### `alerte` — dès la réception d'une alerte (étape 1 du workflow)

Seules les alertes de niveau URGENT ou CRITIQUE sont prises en charge par l'orchestrateur (D33).

```
event: alerte
id: ALR-000001
data: {"id":"ALR-000001","patientId":"TN-0012345","niveau":"CRITIQUE","type":"FREQUENCE_CARDIAQUE","valeur":168.0,"seuil":140.0,"message":"Fréquence cardiaque 168 bpm > 140 bpm","horodatage":"2026-11-22T17:20:00+01:00"}
```

| Champ | Type | Valeurs |
|---|---|---|
| `id` | chaîne | `ALR-` + 6 chiffres (service gRPC) |
| `patientId` | chaîne | `TN-` + 7 chiffres |
| `niveau` | chaîne | `URGENT`, `CRITIQUE` (énumération `Niveau` de `telemetrie.proto`) |
| `type` | chaîne | `FREQUENCE_CARDIAQUE`, `SPO2`, `CHUTE` (énumération `TypeMesure`) |
| `valeur`, `seuil` | nombre | valeur mesurée et seuil franchi |
| `message` | chaîne | libellé de l'alerte |
| `horodatage` | date-heure | instant de la mesure |

### `prise-en-charge` — à la fin du workflow

```
event: prise-en-charge
id: ALR-000001
data: {"alerteId":"ALR-000001","patientId":"TN-0012345","etat":"TERMINE","aRegulariser":false,"rendezVousId":"RDV-000001","debutRendezVous":"2026-11-22T17:30:00+01:00","ficheId":"FIC-000001","horodatage":"2026-11-22T17:20:01+01:00"}
```

| Champ | Type | Valeurs |
|---|---|---|
| `alerteId`, `patientId` | chaîne | alerte et patient concernés |
| `etat` | chaîne | `TERMINE` (rendez-vous + fiche créés), `AUCUN_CRENEAU` (aucun créneau d'urgence libre ; ambulance en P5.2), `ECHEC` (un service n'a pas répondu) |
| `aRegulariser` | booléen | `true` si les droits CNAM ne sont pas ouverts (prise en charge « à régulariser ») |
| `rendezVousId`, `debutRendezVous` | chaîne, date-heure ou `null` | rendez-vous d'urgence réservé (service REST) ; `null` si `etat` ≠ `TERMINE` |
| `ficheId` | chaîne ou `null` | fiche d'urgence créée (tableau de bord GraphQL) ; `null` si `etat` ≠ `TERMINE` |
| `horodatage` | date-heure | fin du workflow |

### Maintien de la connexion

À l'ouverture, l'orchestrateur envoie le commentaire SSE `:connecte` (les en-têtes partent aussitôt). Ensuite, toutes
les 20 secondes, il envoie `:ping`, qui garde la connexion ouverte à travers la gateway et détecte les clients partis.
`EventSource` ignore les commentaires.

## Exemple (navigateur)

```js
const source = new EventSource(`/events?access_token=${jeton}`);
source.addEventListener('alerte', e => afficherAlerte(JSON.parse(e.data)));
source.addEventListener('prise-en-charge', e => afficherFiche(JSON.parse(e.data)));
```
