import { HttpClient } from '@angular/common/http';
import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';

import { ReponseConnexion, Role, Session } from './session';

export const CLE_SESSION = 'smart-healthcare.session';
export const URL_CONNEXION = '/auth/login';

/**
 * Connexion à la gateway (D44) et conservation du jeton JWT (D48) :
 * sessionStorage, suppression à l'expiration (`expireLe`) ou sur demande.
 */
@Injectable({ providedIn: 'root' })
export class AuthService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly etat = signal<Session | null>(null);
  private minuterie: ReturnType<typeof setTimeout> | undefined;

  readonly session = this.etat.asReadonly();
  readonly connecte = computed(() => this.etat() !== null);
  readonly role = computed<Role | null>(() => this.etat()?.role ?? null);

  constructor() {
    const restauree = lireSession();
    if (restauree && restauree.expireLe > Date.now()) {
      this.ouvrir(restauree);
    } else if (restauree) {
      effacerSession();
    }
  }

  connecter(identifiant: string, motDePasse: string): Observable<Session> {
    return this.http.post<ReponseConnexion>(URL_CONNEXION, { identifiant, motDePasse }).pipe(
      map((r) => ({ jeton: r.jeton, expireLe: Date.parse(r.expireLe), sujet: r.sujet, role: r.role })),
      tap((session) => {
        ecrireSession(session);
        this.ouvrir(session);
      }),
    );
  }

  /** Jeton encore valide, sinon `null` (la session expirée est fermée). */
  jeton(): string | null {
    const session = this.etat();
    if (!session) return null;
    if (session.expireLe <= Date.now()) {
      this.deconnecter();
      return null;
    }
    return session.jeton;
  }

  deconnecter(retourConnexion = true): void {
    clearTimeout(this.minuterie);
    effacerSession();
    this.etat.set(null);
    if (retourConnexion) {
      void this.router.navigateByUrl('/connexion');
    }
  }

  ngOnDestroy(): void {
    clearTimeout(this.minuterie);
  }

  private ouvrir(session: Session): void {
    this.etat.set(session);
    clearTimeout(this.minuterie);
    // setTimeout est limité à ~24,8 jours ; le jeton dure 1 h (D44).
    const delai = Math.min(session.expireLe - Date.now(), 2_147_483_647);
    this.minuterie = setTimeout(() => this.deconnecter(), Math.max(delai, 0));
  }
}

function lireSession(): Session | null {
  try {
    const brut = sessionStorage.getItem(CLE_SESSION);
    if (!brut) return null;
    const s = JSON.parse(brut) as Partial<Session>;
    if (typeof s.jeton !== 'string' || typeof s.expireLe !== 'number' || typeof s.sujet !== 'string') return null;
    if (s.role !== 'PATIENT' && s.role !== 'MEDECIN' && s.role !== 'ADMIN') return null;
    return s as Session;
  } catch {
    return null;
  }
}

function ecrireSession(session: Session): void {
  try {
    sessionStorage.setItem(CLE_SESSION, JSON.stringify(session));
  } catch {
    // Stockage indisponible (navigation privée stricte) : la session reste en mémoire.
  }
}

function effacerSession(): void {
  try {
    sessionStorage.removeItem(CLE_SESSION);
  } catch {
    // ignoré
  }
}
