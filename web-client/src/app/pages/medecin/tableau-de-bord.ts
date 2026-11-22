import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

import {
  NiveauRisque,
  PatientResume,
  ServiceHospitalierGql,
  TableauDeBordService,
} from '../../core/api/tableau-de-bord';
import { dateHeure, messageErreur } from '../../core/format';
import { COUVERTURE, NIVEAU, RISQUE } from './libelles';

/**
 * Tableau de bord médecin (P4.4) : liste des patients filtrable par service, pathologie
 * (recherche partielle, D30) et niveaux de risque — requête GraphQL `patients(filtres)`.
 */
@Component({
  selector: 'app-tableau-de-bord',
  imports: [FormsModule, RouterLink],
  templateUrl: './tableau-de-bord.html',
})
export class TableauDeBord {
  private readonly api = inject(TableauDeBordService);

  protected readonly RISQUE = RISQUE;
  protected readonly NIVEAU = NIVEAU;
  protected readonly COUVERTURE = COUVERTURE;
  protected readonly dateHeure = dateHeure;
  protected readonly niveauxRisque: NiveauRisque[] = ['ELEVE', 'MOYEN', 'FAIBLE'];

  protected readonly services = toSignal(this.api.services().pipe(catchError(() => of([] as ServiceHospitalierGql[]))), {
    initialValue: [] as ServiceHospitalierGql[],
  });

  protected serviceCode = '';
  protected pathologie = '';
  protected risques: Record<NiveauRisque, boolean> = { ELEVE: false, MOYEN: false, FAIBLE: false };

  protected readonly patients = signal<PatientResume[] | null>(null);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    this.rechercher();
  }

  rechercher(): void {
    const niveauRisque = this.niveauxRisque.filter((n) => this.risques[n]);
    this.erreur.set(null);
    this.api
      .patients({
        serviceCode: this.serviceCode || undefined,
        pathologie: this.pathologie.trim() || undefined,
        niveauRisque: niveauRisque.length ? niveauRisque : undefined,
      })
      .subscribe({
        next: (liste) => this.patients.set(liste),
        error: (e) => this.erreur.set(e instanceof Error && !('status' in e) ? e.message : messageErreur(e)),
      });
  }

  protected reinitialiser(): void {
    this.serviceCode = '';
    this.pathologie = '';
    this.risques = { ELEVE: false, MOYEN: false, FAIBLE: false };
    this.rechercher();
  }
}
