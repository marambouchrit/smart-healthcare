import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../core/auth/auth.service';
import { Creneau, PortailService, RendezVous } from '../../core/api/portail';
import { dateHeure, heure, jourTunis, messageErreur } from '../../core/format';
import { ReferentielService } from '../../core/referentiel';

/**
 * Espace patient — rendez-vous (P4.2) : recherche de créneaux libres, prise de rendez-vous (POST),
 * changement de créneau (PUT), modification du motif (PATCH), annulation (PATCH statut ANNULE, D28).
 * Le patient est toujours celui du jeton (D48).
 */
@Component({
  selector: 'app-rendez-vous',
  imports: [FormsModule],
  templateUrl: './rendez-vous.html',
})
export class RendezVousPage {
  private readonly portail = inject(PortailService);
  protected readonly ref = inject(ReferentielService);
  private readonly patientId = inject(AuthService).session()?.sujet ?? '';

  protected readonly dateHeure = dateHeure;
  protected readonly heure = heure;
  protected readonly jourMin = jourTunis(0);
  /** Les créneaux sont générés sur 14 jours (D22). */
  protected readonly jourMax = jourTunis(13);

  // Mes rendez-vous
  protected readonly rendezVous = signal<RendezVous[]>([]);
  protected readonly chargement = signal(true);
  protected readonly aVenir = computed(() =>
    this.rendezVous()
      .filter((r) => r.statut === 'CONFIRME' && Date.parse(r.creneau.fin) > Date.now())
      .sort((a, b) => a.creneau.debut.localeCompare(b.creneau.debut)),
  );
  protected readonly passes = computed(() =>
    this.rendezVous()
      .filter((r) => !this.aVenir().includes(r))
      .sort((a, b) => b.creneau.debut.localeCompare(a.creneau.debut)),
  );

  // Recherche de créneaux (les urgences sont réservées par l'orchestrateur, pas par le patient)
  protected readonly servicesConsultation = computed(() => this.ref.services().filter((s) => s.code !== 'URGENCES'));
  protected serviceCode = '';
  protected medecinId = '';
  protected date = jourTunis(0);
  protected readonly creneaux = signal<Creneau[] | null>(null);
  protected readonly recherche = signal(false);

  // Réservation ou changement de créneau
  protected readonly choisi = signal<Creneau | null>(null);
  protected readonly enRemplacement = signal<RendezVous | null>(null);
  protected motif = '';

  // Modification du motif
  protected readonly motifEnEdition = signal<string | null>(null);
  protected nouveauMotif = '';

  protected readonly envoi = signal(false);
  protected readonly message = signal<{ type: 'ok' | 'erreur'; texte: string } | null>(null);

  constructor() {
    this.recharger();
  }

  protected medecinsDuService() {
    return this.ref.medecins().filter((m) => m.serviceCode !== 'URGENCES' && (!this.serviceCode || m.serviceCode === this.serviceCode));
  }

  protected changerService(): void {
    if (this.medecinId && !this.medecinsDuService().some((m) => m.id === this.medecinId)) this.medecinId = '';
  }

  protected chercher(): void {
    this.recherche.set(true);
    this.choisi.set(null);
    this.portail
      .listerCreneaux({ serviceCode: this.serviceCode, medecinId: this.medecinId, date: this.date, disponible: true })
      .subscribe({
        next: (liste) => {
          const maintenant = Date.now();
          this.creneaux.set(
            liste
              .filter((c) => c.serviceCode !== 'URGENCES' && Date.parse(c.debut) > maintenant)
              .sort((a, b) => a.debut.localeCompare(b.debut)),
          );
          this.recherche.set(false);
        },
        error: (e) => {
          this.recherche.set(false);
          this.afficherErreur(e);
        },
      });
  }

  protected choisir(c: Creneau): void {
    this.choisi.set(c);
    this.message.set(null);
  }

  protected commencerRemplacement(r: RendezVous): void {
    this.enRemplacement.set(r);
    this.motif = r.motif ?? '';
    this.serviceCode = r.creneau.serviceCode;
    this.medecinId = r.creneau.medecinId;
    this.creneaux.set(null);
    this.choisi.set(null);
    this.message.set(null);
  }

  protected abandonner(): void {
    this.enRemplacement.set(null);
    this.choisi.set(null);
    this.motif = '';
  }

  protected confirmer(): void {
    const creneau = this.choisi();
    if (!creneau || this.envoi()) return;
    const corps = { patientId: this.patientId, creneauId: creneau.id, motif: this.motif.trim() || undefined };
    const remplace = this.enRemplacement();
    const appel = remplace
      ? this.portail.remplacerRendezVous(remplace.id, corps)
      : this.portail.creerRendezVous(corps);
    this.envoi.set(true);
    appel.subscribe({
      next: (r) => {
        this.envoi.set(false);
        this.message.set({
          type: 'ok',
          texte: `${remplace ? 'Rendez-vous déplacé' : 'Rendez-vous confirmé'} : ${r.id}, ${dateHeure(r.creneau.debut)}.`,
        });
        this.abandonner();
        this.creneaux.set(null);
        this.recharger();
      },
      error: (e) => {
        this.envoi.set(false);
        this.afficherErreur(e);
        this.chercher();
      },
    });
  }

  protected editerMotif(r: RendezVous): void {
    this.motifEnEdition.set(r.id);
    this.nouveauMotif = r.motif ?? '';
  }

  protected enregistrerMotif(r: RendezVous): void {
    if (this.envoi()) return;
    this.envoi.set(true);
    this.portail.modifierRendezVous(r.id, { motif: this.nouveauMotif.trim() }).subscribe({
      next: () => {
        this.envoi.set(false);
        this.motifEnEdition.set(null);
        this.message.set({ type: 'ok', texte: `Motif du rendez-vous ${r.id} modifié.` });
        this.recharger();
      },
      error: (e) => {
        this.envoi.set(false);
        this.afficherErreur(e);
      },
    });
  }

  protected annuler(r: RendezVous): void {
    if (this.envoi() || !confirm(`Annuler le rendez-vous du ${dateHeure(r.creneau.debut)} ?`)) return;
    this.envoi.set(true);
    this.portail.modifierRendezVous(r.id, { statut: 'ANNULE' }).subscribe({
      next: () => {
        this.envoi.set(false);
        this.message.set({ type: 'ok', texte: `Rendez-vous ${r.id} annulé : le créneau est de nouveau libre.` });
        if (this.enRemplacement()?.id === r.id) this.abandonner();
        this.recharger();
      },
      error: (e) => {
        this.envoi.set(false);
        this.afficherErreur(e);
        this.recharger();
      },
    });
  }

  protected libelleStatut(r: RendezVous): string {
    if (r.statut === 'ANNULE') return 'Annulé';
    if (r.statut === 'HONORE') return 'Honoré';
    return Date.parse(r.creneau.fin) > Date.now() ? 'Confirmé' : 'Passé';
  }

  private recharger(): void {
    this.portail.listerRendezVous({ patientId: this.patientId }).subscribe({
      next: (liste) => {
        this.rendezVous.set(liste);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.afficherErreur(e);
      },
    });
  }

  private afficherErreur(e: unknown): void {
    this.message.set({ type: 'erreur', texte: messageErreur(e) });
  }
}
