import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-espace-patient',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <h1>Mon espace</h1>
    <nav class="onglets">
      <a routerLink="rendez-vous" routerLinkActive="actif">Rendez-vous</a>
      <a routerLink="ordonnances" routerLinkActive="actif">Ordonnances</a>
      <a routerLink="profil" routerLinkActive="actif">Profil</a>
    </nav>
    <router-outlet />
  `,
})
export class EspacePatient {}
