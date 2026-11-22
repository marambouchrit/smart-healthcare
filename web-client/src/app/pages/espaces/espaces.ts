import { Component } from '@angular/core';

/*
 * Pages d'accueil de chaque rôle (D48). Elles seront remplies par les tâches suivantes :
 * médecin → P4.4 / P4.5 (GraphQL, SSE), administrateur → P4.6 (SOAP).
 */

@Component({
  selector: 'app-espace-medecin',
  template: `<h1>Tableau de bord</h1>
    <p>Patients, fiches et alertes en direct : à venir (P4.4, P4.5).</p>`,
})
export class EspaceMedecin {}

@Component({
  selector: 'app-espace-admin',
  template: `<h1>Administration</h1>
    <p>Droits CNAM et historique certifié : à venir (P4.6).</p>`,
})
export class EspaceAdmin {}
