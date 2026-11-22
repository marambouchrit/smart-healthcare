import { Component, OnDestroy, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AlertesDirectService } from '../../core/api/alertes-direct';
import { AlertesEnDirect } from './alertes-en-direct';

/**
 * Espace médecin (D48) : alertes en direct (SSE, P4.5) au-dessus du tableau de bord GraphQL (P4.4).
 * Le flux est ouvert tant que l'espace est affiché.
 */
@Component({
  selector: 'app-espace-medecin',
  imports: [RouterOutlet, AlertesEnDirect],
  template: `
    <app-alertes-en-direct />
    <router-outlet />
  `,
})
export class EspaceMedecin implements OnDestroy {
  private readonly direct = inject(AlertesDirectService);

  constructor() {
    this.direct.ouvrir();
  }

  ngOnDestroy(): void {
    this.direct.fermer();
  }
}
