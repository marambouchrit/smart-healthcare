import { Injectable, InjectionToken, OnDestroy, effect, inject, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

import { AuthService } from '../auth/auth.service';

/*
 * Flux SSE `/events` de l'orchestrateur via la gateway (contrat : contracts/sse/README.md, D45).
 * EventSource ne peut pas envoyer d'en-tête : le jeton passe en `?access_token=` (seul chemin autorisé, D44).
 * Le flux ne rejoue pas le passé : l'historique se lit dans le tableau de bord GraphQL.
 */

export interface EvenementAlerte {
  id: string;
  patientId: string;
  niveau: 'URGENT' | 'CRITIQUE';
  type: 'FREQUENCE_CARDIAQUE' | 'SPO2' | 'CHUTE';
  valeur: number;
  seuil: number;
  message: string;
  horodatage: string;
}

export interface EvenementPriseEnCharge {
  alerteId: string;
  patientId: string;
  etat: 'TERMINE' | 'AUCUN_CRENEAU' | 'ECHEC';
  aRegulariser: boolean;
  rendezVousId: string | null;
  debutRendezVous: string | null;
  ficheId: string | null;
  horodatage: string;
}

export type EvenementDirect =
  | { type: 'alerte'; donnees: EvenementAlerte }
  | { type: 'prise-en-charge'; donnees: EvenementPriseEnCharge };

/** Alerte reçue en direct, complétée par le résultat de sa prise en charge quand il arrive. */
export interface AlerteEnDirect {
  alerte: EvenementAlerte;
  priseEnCharge: EvenementPriseEnCharge | null;
  recueLe: number;
}

export type EtatConnexion = 'ferme' | 'connexion' | 'ouvert' | 'erreur';

/** Fabrique d'EventSource (remplacée dans les tests : jsdom n'en fournit pas). */
export const FABRIQUE_EVENT_SOURCE = new InjectionToken<(url: string) => EventSource>('FABRIQUE_EVENT_SOURCE', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url),
});

/** Nombre d'alertes gardées à l'écran. */
export const MAX_ALERTES = 20;

@Injectable({ providedIn: 'root' })
export class AlertesDirectService implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly fabrique = inject(FABRIQUE_EVENT_SOURCE);

  private source: EventSource | null = null;
  private readonly flux = new Subject<EvenementDirect>();

  readonly etat = signal<EtatConnexion>('ferme');
  readonly alertes = signal<AlerteEnDirect[]>([]);
  /** Chaque événement reçu (alerte ou prise en charge), pour rafraîchir les écrans concernés. */
  readonly evenements: Observable<EvenementDirect> = this.flux.asObservable();

  constructor() {
    // Déconnexion (bouton, expiration ou 401) : fermer le flux et oublier les alertes affichées.
    effect(() => {
      if (!this.auth.session()) this.reinitialiser();
    });
  }

  /** Ouvre le flux s'il ne l'est pas déjà (appelé par l'espace médecin). */
  ouvrir(): void {
    if (this.source) return;
    const jeton = this.auth.jeton();
    if (!jeton) return;
    this.etat.set('connexion');
    const source = this.fabrique(`/events?access_token=${encodeURIComponent(jeton)}`);
    this.source = source;
    source.onopen = () => this.etat.set('ouvert');
    source.onerror = () => {
      // CONNECTING : EventSource se reconnecte seul (fermeture après 30 min, D45).
      // CLOSED : refus définitif (401 si le jeton a expiré) → la session est vérifiée.
      if (source.readyState === 2) {
        this.fermer();
        this.etat.set('erreur');
        this.auth.jeton();
      } else {
        this.etat.set('connexion');
      }
    };
    source.addEventListener('alerte', (e) => this.recevoirAlerte(e as MessageEvent<string>));
    source.addEventListener('prise-en-charge', (e) => this.recevoirPriseEnCharge(e as MessageEvent<string>));
  }

  fermer(): void {
    this.source?.close();
    this.source = null;
    this.etat.set('ferme');
  }

  /** À la déconnexion : fermer le flux et oublier les alertes affichées. */
  reinitialiser(): void {
    this.fermer();
    this.alertes.set([]);
  }

  ngOnDestroy(): void {
    this.fermer();
  }

  private recevoirAlerte(e: MessageEvent<string>): void {
    const alerte = lire<EvenementAlerte>(e.data);
    if (!alerte) return;
    this.alertes.update((liste) =>
      [{ alerte, priseEnCharge: null, recueLe: Date.now() }, ...liste.filter((a) => a.alerte.id !== alerte.id)].slice(
        0,
        MAX_ALERTES,
      ),
    );
    this.flux.next({ type: 'alerte', donnees: alerte });
  }

  private recevoirPriseEnCharge(e: MessageEvent<string>): void {
    const pec = lire<EvenementPriseEnCharge>(e.data);
    if (!pec) return;
    this.alertes.update((liste) =>
      liste.map((a) => (a.alerte.id === pec.alerteId ? { ...a, priseEnCharge: pec } : a)),
    );
    this.flux.next({ type: 'prise-en-charge', donnees: pec });
  }
}

function lire<T>(texte: string): T | null {
  try {
    return JSON.parse(texte) as T;
  } catch {
    return null;
  }
}
