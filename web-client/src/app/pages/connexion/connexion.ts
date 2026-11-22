import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { ACCUEIL_PAR_ROLE } from '../../core/auth/session';

@Component({
  selector: 'app-connexion',
  imports: [ReactiveFormsModule],
  templateUrl: './connexion.html',
  styleUrl: './connexion.css',
})
export class Connexion {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    identifiant: ['', Validators.required],
    motDePasse: ['', Validators.required],
  });
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected envoyer(): void {
    if (this.formulaire.invalid || this.enCours()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const { identifiant, motDePasse } = this.formulaire.getRawValue();
    this.enCours.set(true);
    this.erreur.set(null);
    this.auth.connecter(identifiant.trim(), motDePasse).subscribe({
      next: (session) => {
        this.enCours.set(false);
        void this.router.navigateByUrl(ACCUEIL_PAR_ROLE[session.role]);
      },
      error: (e: unknown) => {
        this.enCours.set(false);
        this.erreur.set(messageErreur(e));
      },
    });
  }
}

function messageErreur(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 401) return 'Identifiant ou mot de passe incorrect.';
    if (e.status === 400) return 'Saisissez un identifiant et un mot de passe.';
    if (e.status === 0 || e.status >= 500) return 'La plateforme est injoignable. Vérifiez que la gateway est démarrée.';
  }
  return 'Connexion impossible. Réessayez.';
}
