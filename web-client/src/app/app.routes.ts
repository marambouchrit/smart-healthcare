import { Routes } from '@angular/router';

import { invitesSeulement, redirectionAccueil, roleGuard } from './core/auth/role.guard';
import { Connexion } from './pages/connexion/connexion';
import { EspaceAdmin, EspaceMedecin } from './pages/espaces/espaces';
import { EspacePatient } from './pages/patient/espace-patient';
import { RendezVousPage } from './pages/patient/rendez-vous';

/** Écrans par rôle (D48). */
export const routes: Routes = [
  { path: 'connexion', component: Connexion, canActivate: [invitesSeulement], title: 'Connexion — Smart Healthcare' },
  {
    path: 'patient',
    component: EspacePatient,
    canActivate: [roleGuard('PATIENT')],
    children: [
      { path: 'rendez-vous', component: RendezVousPage, title: 'Mes rendez-vous' },
      { path: '', pathMatch: 'full', redirectTo: 'rendez-vous' },
    ],
  },
  { path: 'medecin', component: EspaceMedecin, canActivate: [roleGuard('MEDECIN', 'ADMIN')], title: 'Tableau de bord' },
  { path: 'admin', component: EspaceAdmin, canActivate: [roleGuard('ADMIN')], title: 'Administration' },
  { path: '', pathMatch: 'full', canActivate: [redirectionAccueil], children: [] },
  { path: '**', redirectTo: '' },
];
