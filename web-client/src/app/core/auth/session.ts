/** Rôles émis par la gateway (D44) et écrans associés (D48). */
export type Role = 'PATIENT' | 'MEDECIN' | 'ADMIN';

/** Réponse de `POST /auth/login` (gateway, D44). */
export interface ReponseConnexion {
  jeton: string;
  type: 'Bearer';
  /** Instant ISO 8601 (UTC). */
  expireLe: string;
  /** Identifiant lié au compte : `TN-xxxxxxx` (patient), `MED-xxx` (médecin) ou `admin`. */
  sujet: string;
  role: Role;
}

/** Session conservée dans le navigateur (sessionStorage, D48). */
export interface Session {
  jeton: string;
  expireLe: number;
  sujet: string;
  role: Role;
}

/** Page d'accueil de chaque rôle. */
export const ACCUEIL_PAR_ROLE: Record<Role, string> = {
  PATIENT: '/patient',
  MEDECIN: '/medecin',
  ADMIN: '/admin',
};
