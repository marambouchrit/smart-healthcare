import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../core/auth/auth.service';
import { Ordonnance, PortailService, StatutTraitement } from '../../core/api/portail';
import { dateLongue, messageErreur } from '../../core/format';
import { ReferentielService } from '../../core/referentiel';

/** Espace patient — ordonnances (P4.3) : `GET /api/patients/{id}/ordonnances`, filtre par statut. */
@Component({
  selector: 'app-ordonnances',
  imports: [FormsModule],
  template: `
    <section class="panneau">
      <div class="filtres">
        <h2 style="margin: 0; flex: 1">Mes ordonnances</h2>
        <label>
          Traitements
          <select name="statut" [(ngModel)]="statut" (ngModelChange)="charger()">
            <option value="">Tous</option>
            <option value="EN_COURS">En cours</option>
            <option value="TERMINE">Terminés</option>
          </select>
        </label>
      </div>

      @if (erreur(); as e) {
        <p class="bandeau erreur" role="alert">{{ e }}</p>
      } @else if (ordonnances() === null) {
        <p class="discret">Chargement…</p>
      } @else if (ordonnances()!.length === 0) {
        <p class="discret">Aucune ordonnance.</p>
      } @else {
        @for (o of ordonnances(); track o.id) {
          <article class="ordonnance" [attr.data-ordonnance]="o.id">
            <h3>
              {{ dateLongue(o.date) }} · {{ ref.nomMedecin(o.medecinId) }}
              <span class="discret">({{ o.id }})</span>
            </h3>
            <table>
              <thead>
                <tr>
                  <th>Médicament</th>
                  <th>Dosage</th>
                  <th>Posologie</th>
                  <th>Période</th>
                  <th>Statut</th>
                </tr>
              </thead>
              <tbody>
                @for (l of o.lignes; track l.traitementId) {
                  <tr>
                    <td>{{ l.medicament }}</td>
                    <td>{{ l.dosage }}</td>
                    <td>{{ l.posologie }}</td>
                    <td>
                      depuis le {{ dateLongue(l.dateDebut) }}
                      @if (l.dateFin) {
                        <br />jusqu'au {{ dateLongue(l.dateFin) }}
                      }
                    </td>
                    <td>
                      <span class="badge">{{ l.statut === 'EN_COURS' ? 'En cours' : 'Terminé' }}</span>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </article>
        }
      }
    </section>
  `,
  styles: `
    .ordonnance + .ordonnance {
      margin-top: 24px;
    }
    h3 {
      margin: 0 0 8px;
      font-size: 1rem;
    }
  `,
})
export class OrdonnancesPage {
  private readonly portail = inject(PortailService);
  protected readonly ref = inject(ReferentielService);
  private readonly patientId = inject(AuthService).session()?.sujet ?? '';

  protected readonly dateLongue = dateLongue;
  protected statut: StatutTraitement | '' = '';
  protected readonly ordonnances = signal<Ordonnance[] | null>(null);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    this.charger();
  }

  protected charger(): void {
    this.erreur.set(null);
    this.portail.listerOrdonnances(this.patientId, this.statut || undefined).subscribe({
      next: (liste) => this.ordonnances.set(liste),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }
}
