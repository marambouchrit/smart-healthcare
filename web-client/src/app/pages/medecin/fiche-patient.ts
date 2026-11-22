import { Component, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, filter } from 'rxjs';

import { AlertesDirectService } from '../../core/api/alertes-direct';

import { DossierPatient, HistoriqueSoins, TableauDeBordService } from '../../core/api/tableau-de-bord';
import { dateHeure, dateLongue, jourTunis, messageErreur } from '../../core/format';
import { COUVERTURE, MESURE, NIVEAU, RISQUE, valeurMesure } from './libelles';

/**
 * Fiche patient consolidée (P4.4) : une requête GraphQL `patient(id)` (profil, couverture, constantes,
 * alertes, traitements, fiches d'urgence) et l'agrégat `historiqueSoins(depuis, jusqua)` à la demande.
 * P4.5 : un événement SSE (alerte ou prise en charge) concernant ce patient relit la fiche sans recharger la page.
 */
@Component({
  selector: 'app-fiche-patient',
  imports: [FormsModule, RouterLink],
  templateUrl: './fiche-patient.html',
})
export class FichePatient {
  private readonly api = inject(TableauDeBordService);

  /** Paramètre de route `:id` (withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly RISQUE = RISQUE;
  protected readonly NIVEAU = NIVEAU;
  protected readonly MESURE = MESURE;
  protected readonly COUVERTURE = COUVERTURE;
  protected readonly dateHeure = dateHeure;
  protected readonly dateLongue = dateLongue;
  protected readonly valeurMesure = valeurMesure;

  protected readonly dossier = signal<DossierPatient | null | undefined>(undefined);
  protected readonly erreur = signal<string | null>(null);

  protected depuis = jourTunis(-30);
  protected jusqua = jourTunis(0);
  protected readonly historique = signal<HistoriqueSoins | null>(null);

  constructor() {
    effect(() => {
      const id = this.id();
      this.dossier.set(undefined);
      this.historique.set(null);
      this.charger(id);
      this.calculerHistorique();
    });

    inject(AlertesDirectService)
      .evenements.pipe(
        filter((e) => e.donnees.patientId === this.id()),
        debounceTime(300),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.recharger());
  }

  /** Relit le dossier sans vider l'écran (utilisé aussi par les mises à jour en direct, P4.5). */
  recharger(): void {
    this.charger(this.id());
    this.calculerHistorique();
  }

  protected repartition(h: HistoriqueSoins): string {
    return h.alertesParNiveau.map((n) => `${NIVEAU[n.niveau]} ${n.nombre}`).join(', ');
  }

  protected calculerHistorique(): void {
    this.api.historique(this.id(), this.depuis, this.jusqua).subscribe({
      next: (h) => this.historique.set(h),
      error: (e) => this.erreur.set(texte(e)),
    });
  }

  private charger(id: string): void {
    this.api.dossier(id).subscribe({
      next: (d) => {
        this.erreur.set(null);
        this.dossier.set(d);
      },
      error: (e) => this.erreur.set(texte(e)),
    });
  }
}

function texte(e: unknown): string {
  return e instanceof Error && !('status' in e) ? e.message : messageErreur(e);
}
