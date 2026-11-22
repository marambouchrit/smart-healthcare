import { Routes } from '@angular/router';

import { invitesSeulement, redirectionAccueil, roleGuard } from './core/auth/role.guard';
import { Connexion } from './pages/connexion/connexion';
import { EspaceAdmin } from './pages/espaces/espaces';
import { EspaceMedecin } from './pages/medecin/espace-medecin';
import { FichePatient } from './pages/medecin/fiche-patient';
import { TableauDeBord } from './pages/medecin/tableau-de-bord';
import { EspacePatient } from './pages/patient/espace-patient';
import { OrdonnancesPage } from './pages/patient/ordonnances';
import { ProfilPage } from './pages/patient/profil';
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
      { path: 'ordonnances', component: OrdonnancesPage, title: 'Mes ordonnances' },
      { path: 'profil', component: ProfilPage, title: 'Mon profil' },
      { path: '', pathMatch: 'full', redirectTo: 'rendez-vous' },
    ],
  },
  {
    path: 'medecin',
    component: EspaceMedecin,
    canActivate: [roleGuard('MEDECIN', 'ADMIN')],
    children: [
      { path: '', component: TableauDeBord, title: 'Tableau de bord' },
      { path: 'patients/:id', component: FichePatient, title: 'Fiche patient' },
    ],
  },
  { path: 'admin', component: EspaceAdmin, canActivate: [roleGuard('ADMIN')], title: 'Administration' },
  { path: '', pathMatch: 'full', canActivate: [redirectionAccueil], children: [] },
  { path: '**', redirectTo: '' },
];
