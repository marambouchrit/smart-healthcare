import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../../core/auth/auth.service';
import { PortailService, ProfilModification, ProfilPatient } from '../../core/api/portail';
import { dateLongue, messageErreur } from '../../core/format';
import { ReferentielService } from '../../core/referentiel';

/**
 * Espace patient — profil (P4.3) : `GET /api/patients/{id}` et `PATCH` des seules coordonnées
 * (schéma ProfilModification : téléphone, e-mail, adresse complète). Seuls les champs modifiés sont envoyés.
 */
@Component({
  selector: 'app-profil',
  imports: [ReactiveFormsModule],
  templateUrl: './profil.html',
})
export class ProfilPage {
  private readonly portail = inject(PortailService);
  protected readonly ref = inject(ReferentielService);
  private readonly patientId = inject(AuthService).session()?.sujet ?? '';

  protected readonly dateLongue = dateLongue;
  protected readonly profil = signal<ProfilPatient | null>(null);
  protected readonly envoi = signal(false);
  protected readonly message = signal<{ type: 'ok' | 'erreur'; texte: string } | null>(null);

  /** Mêmes règles que le contrat (ProfilModification, Adresse). */
  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    telephone: ['', Validators.pattern(/^\+?[0-9 ]{8,20}$/)],
    email: ['', Validators.email],
    rue: ['', Validators.required],
    ville: ['', Validators.required],
    codePostal: ['', [Validators.required, Validators.pattern(/^[0-9]{4}$/)]],
  });

  constructor() {
    this.portail.obtenirProfil(this.patientId).subscribe({
      next: (p) => this.afficher(p),
      error: (e) => this.message.set({ type: 'erreur', texte: messageErreur(e) }),
    });
  }

  protected enregistrer(): void {
    const p = this.profil();
    if (!p || this.envoi()) return;
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const v = this.formulaire.getRawValue();
    const corps: ProfilModification = {};
    if (v.telephone.trim() && v.telephone.trim() !== (p.telephone ?? '')) corps.telephone = v.telephone.trim();
    if (v.email.trim() && v.email.trim() !== (p.email ?? '')) corps.email = v.email.trim();
    const adresse = { rue: v.rue.trim(), ville: v.ville.trim(), codePostal: v.codePostal.trim() };
    if (adresse.rue !== p.adresse.rue || adresse.ville !== p.adresse.ville || adresse.codePostal !== p.adresse.codePostal) {
      corps.adresse = adresse;
    }
    if (Object.keys(corps).length === 0) {
      this.message.set({ type: 'ok', texte: 'Aucune modification à enregistrer.' });
      return;
    }
    this.envoi.set(true);
    this.portail.modifierProfil(this.patientId, corps).subscribe({
      next: (maj) => {
        this.envoi.set(false);
        this.afficher(maj);
        this.message.set({ type: 'ok', texte: 'Coordonnées mises à jour.' });
      },
      error: (e) => {
        this.envoi.set(false);
        this.message.set({ type: 'erreur', texte: messageErreur(e) });
      },
    });
  }

  protected annulerSaisie(): void {
    const p = this.profil();
    if (p) this.afficher(p);
    this.message.set(null);
  }

  private afficher(p: ProfilPatient): void {
    this.profil.set(p);
    this.formulaire.reset({
      telephone: p.telephone ?? '',
      email: p.email ?? '',
      rue: p.adresse.rue,
      ville: p.adresse.ville,
      codePostal: p.adresse.codePostal,
    });
  }
}
