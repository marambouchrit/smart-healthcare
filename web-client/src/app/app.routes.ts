import { Routes } from '@angular/router';

import { invitesSeulement, redirectionAccueil, roleGuard } from './core/auth/role.guard';
import { Connexion } from './pages/connexion/connexion';
import { EspaceAdmin, EspaceMedecin, EspacePatient } from './pages/espaces/espaces';

/** Écrans par rôle (D48). */
export const routes: Routes = [
  { path: 'connexion', component: Connexion, canActivate: [invitesSeulement], title: 'Connexion — Smart Healthcare' },
  { path: 'patient', component: EspacePatient, canActivate: [roleGuard('PATIENT')], title: 'Espace patient' },
  { path: 'medecin', component: EspaceMedecin, canActivate: [roleGuard('MEDECIN', 'ADMIN')], title: 'Tableau de bord' },
  { path: 'admin', component: EspaceAdmin, canActivate: [roleGuard('ADMIN')], title: 'Administration' },
  { path: '', pathMatch: 'full', canActivate: [redirectionAccueil], children: [] },
  { path: '**', redirectTo: '' },
];
