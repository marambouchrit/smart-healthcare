import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';
import { ACCUEIL_PAR_ROLE, Role } from './session';

/**
 * Accès réservé aux rôles listés (D48). Sans session valide → `/connexion` ;
 * connecté avec un autre rôle → page d'accueil de son rôle.
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const role = auth.jeton() ? auth.role() : null;
    if (!role) return router.parseUrl('/connexion');
    return roles.includes(role) ? true : router.parseUrl(ACCUEIL_PAR_ROLE[role]);
  };
}

/** Racine `/` : page d'accueil du rôle connecté, sinon `/connexion`. */
export const redirectionAccueil: CanActivateFn = () => {
  const auth = inject(AuthService);
  const role = auth.jeton() ? auth.role() : null;
  return inject(Router).parseUrl(role ? ACCUEIL_PAR_ROLE[role] : '/connexion');
};

/** Page de connexion : un utilisateur déjà connecté est renvoyé vers son accueil. */
export const invitesSeulement: CanActivateFn = () => {
  const auth = inject(AuthService);
  const role = auth.jeton() ? auth.role() : null;
  return role ? inject(Router).parseUrl(ACCUEIL_PAR_ROLE[role]) : true;
};
