import { Component, computed, inject } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';

/*
 * Pages d'accueil de chaque rôle (D48). Elles seront remplies par les tâches suivantes :
 * patient → P4.2 / P4.3 (REST), médecin → P4.4 / P4.5 (GraphQL, SSE), administrateur → P4.6 (SOAP).
 */

@Component({
  selector: 'app-espace-patient',
  template: `<h1>Espace patient</h1>
    <p>Dossier {{ sujet() }}. Rendez-vous, ordonnances et profil : à venir (P4.2, P4.3).</p>`,
})
export class EspacePatient {
  private readonly auth = inject(AuthService);
  protected readonly sujet = computed(() => this.auth.session()?.sujet);
}

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
