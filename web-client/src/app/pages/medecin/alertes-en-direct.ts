import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

import { AlerteEnDirect, AlertesDirectService } from '../../core/api/alertes-direct';
import { TableauDeBordService } from '../../core/api/tableau-de-bord';
import { dateHeure, heure } from '../../core/format';
import { NIVEAU } from './libelles';

/** Panneau des alertes reçues en direct par SSE et du résultat de leur prise en charge (P4.5). */
@Component({
  selector: 'app-alertes-en-direct',
  imports: [RouterLink],
  template: `
    <section class="panneau direct" aria-live="polite">
      <div class="entete">
        <h2>Alertes en direct</h2>
        <span class="etat" [attr.data-etat]="direct.etat()">
          <span class="point"></span>{{ libelleEtat[direct.etat()] }}
        </span>
      </div>
      @if (direct.alertes().length === 0) {
        <p class="discret">Aucune alerte depuis l'ouverture de la page. Les nouvelles alertes URGENT et CRITIQUE s'affichent ici sans recharger.</p>
      } @else {
        <ul class="liste">
          @for (a of direct.alertes(); track a.alerte.id) {
            <li class="ligne" [attr.data-direct]="a.alerte.id" [class.nouvelle]="a.priseEnCharge === null">
              <div>
                <span class="badge" [class.critique]="a.alerte.niveau === 'CRITIQUE'" [class.urgent]="a.alerte.niveau === 'URGENT'">
                  {{ NIVEAU[a.alerte.niveau] }}
                </span>
                <a [routerLink]="['/medecin/patients', a.alerte.patientId]">{{ nom(a.alerte.patientId) }}</a>
                — {{ a.alerte.message }}
                <div class="discret">{{ a.alerte.id }} · {{ dateHeure(a.alerte.horodatage) }}</div>
              </div>
              <div class="resultat">{{ resultat(a) }}</div>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    .entete {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
    }
    .etat {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      color: var(--texte-secondaire);
      font-size: 0.9rem;
    }
    .point {
      width: 9px;
      height: 9px;
      border-radius: 50%;
      background: var(--texte-secondaire);
    }
    [data-etat='ouvert'] .point {
      background: var(--ok);
    }
    [data-etat='erreur'] .point {
      background: var(--danger);
    }
    .nouvelle {
      background: var(--danger-fond);
    }
    .resultat {
      align-self: center;
      font-weight: 600;
    }
    li {
      padding-left: 8px;
      padding-right: 8px;
    }
  `,
})
export class AlertesEnDirect {
  protected readonly direct = inject(AlertesDirectService);
  protected readonly NIVEAU = NIVEAU;
  protected readonly dateHeure = dateHeure;
  protected readonly libelleEtat = { ferme: 'Déconnecté', connexion: 'Connexion…', ouvert: 'En direct', erreur: 'Flux interrompu' };

  private readonly noms = signal(new Map<string, string>());

  constructor() {
    inject(TableauDeBordService)
      .patients({})
      .pipe(catchError(() => of([])))
      .subscribe((liste) => this.noms.set(new Map(liste.map((p) => [p.id, `${p.prenom} ${p.nom}`]))));
  }

  protected nom(patientId: string): string {
    const nom = this.noms().get(patientId);
    return nom ? `${nom} (${patientId})` : patientId;
  }

  protected resultat(a: AlerteEnDirect): string {
    const p = a.priseEnCharge;
    if (!p) return 'Prise en charge en cours…';
    if (p.etat === 'ECHEC') return 'Échec de la prise en charge (service indisponible)';
    if (p.etat === 'AUCUN_CRENEAU') return 'Aucun créneau d’urgence libre';
    const rdv = p.rendezVousId ? `${p.rendezVousId} à ${heure(p.debutRendezVous!)}` : '';
    return `Fiche ${p.ficheId} · rendez-vous ${rdv}${p.aRegulariser ? ' · à régulariser' : ''}`;
  }
}
