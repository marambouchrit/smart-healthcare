import { HttpErrorResponse } from '@angular/common/http';

/** Fuseau de l'hôpital : toutes les dates des services sont en heure de Tunis (D22, D45). */
export const FUSEAU = 'Africa/Tunis';

const formatDateHeure = new Intl.DateTimeFormat('fr-FR', {
  timeZone: FUSEAU,
  weekday: 'short',
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});
const formatHeure = new Intl.DateTimeFormat('fr-FR', { timeZone: FUSEAU, hour: '2-digit', minute: '2-digit' });
const formatDate = new Intl.DateTimeFormat('fr-FR', { timeZone: FUSEAU, day: '2-digit', month: 'long', year: 'numeric' });
const formatJourIso = new Intl.DateTimeFormat('en-CA', { timeZone: FUSEAU, year: 'numeric', month: '2-digit', day: '2-digit' });

export function dateHeure(iso: string | null | undefined): string {
  return iso ? formatDateHeure.format(new Date(iso)) : '—';
}

export function heure(iso: string): string {
  return formatHeure.format(new Date(iso));
}

/** Date seule (`AAAA-MM-JJ`, sans fuseau) affichée en toutes lettres. */
export function dateLongue(jour: string | null | undefined): string {
  if (!jour) return '—';
  const [a, m, j] = jour.split('-').map(Number);
  return formatDate.format(new Date(Date.UTC(a, m - 1, j, 12)));
}

/** Jour `AAAA-MM-JJ` à Tunis, décalé de `decalage` jours. */
export function jourTunis(decalage = 0, maintenant = Date.now()): string {
  return formatJourIso.format(new Date(maintenant + decalage * 86_400_000));
}

/** Message lisible pour une erreur HTTP : `detail` du Problem Details (D19) si présent. */
export function messageErreur(e: unknown, defaut = "L'opération a échoué."): string {
  if (e instanceof HttpErrorResponse) {
    const corps = e.error as { detail?: unknown; title?: unknown } | null;
    if (corps && typeof corps === 'object' && typeof corps.detail === 'string') return corps.detail;
    if (corps && typeof corps === 'object' && typeof corps.title === 'string') return corps.title;
    if (e.status === 0 || e.status === 502 || e.status === 503 || e.status === 504) {
      return 'Service injoignable. Réessayez dans un instant.';
    }
    if (e.status === 404) return 'Élément introuvable.';
  }
  return defaut;
}
