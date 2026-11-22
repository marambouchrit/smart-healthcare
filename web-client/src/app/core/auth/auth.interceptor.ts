import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService, URL_CONNEXION } from './auth.service';

/**
 * Ajoute `Authorization: Bearer <jeton>` aux appels vers la gateway (URL relatives, même origine)
 * et ferme la session si la gateway répond 401 (jeton expiré ou refusé).
 * Le jeton n'est jamais envoyé à une autre origine.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const versGateway = req.url.startsWith('/') && !req.url.startsWith('//');
  if (!versGateway || req.url === URL_CONNEXION) {
    return next(req);
  }

  const jeton = auth.jeton();
  const requete = jeton ? req.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : req;

  return next(requete).pipe(
    catchError((erreur: unknown) => {
      if (erreur instanceof HttpErrorResponse && erreur.status === 401) {
        auth.deconnecter();
      }
      return throwError(() => erreur);
    }),
  );
};
