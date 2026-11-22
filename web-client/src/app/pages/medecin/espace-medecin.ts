import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Espace médecin (D48) : tableau de bord GraphQL (P4.4). */
@Component({
  selector: 'app-espace-medecin',
  imports: [RouterOutlet],
  template: `<router-outlet />`,
})
export class EspaceMedecin {}
