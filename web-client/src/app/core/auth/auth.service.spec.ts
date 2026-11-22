import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService, CLE_SESSION } from './auth.service';
import { ReponseConnexion } from './session';

const REPONSE_MEDECIN: ReponseConnexion = {
  jeton: 'jeton.medecin',
  type: 'Bearer',
  expireLe: new Date(Date.now() + 3_600_000).toISOString(),
  sujet: 'MED-001',
  role: 'MEDECIN',
};

function creer(): { auth: AuthService; http: HttpTestingController; router: Router } {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
  return {
    auth: TestBed.inject(AuthService),
    http: TestBed.inject(HttpTestingController),
    router: TestBed.inject(Router),
  };
}

describe('AuthService', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => vi.useRealTimers());

  it('envoie identifiant et mot de passe à /auth/login puis conserve la session en sessionStorage', () => {
    const { auth, http } = creer();
    let role: string | undefined;
    auth.connecter('medecin', 'medecin_dev').subscribe((s) => (role = s.role));

    const req = http.expectOne('/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ identifiant: 'medecin', motDePasse: 'medecin_dev' });
    req.flush(REPONSE_MEDECIN);

    expect(role).toBe('MEDECIN');
    expect(auth.connecte()).toBe(true);
    expect(auth.jeton()).toBe('jeton.medecin');
    expect(auth.session()?.sujet).toBe('MED-001');
    const stockee = JSON.parse(sessionStorage.getItem(CLE_SESSION)!);
    expect(stockee).toEqual({
      jeton: 'jeton.medecin',
      expireLe: Date.parse(REPONSE_MEDECIN.expireLe),
      sujet: 'MED-001',
      role: 'MEDECIN',
    });
    http.verify();
  });

  it("n'ouvre aucune session si la gateway répond 401", () => {
    const { auth, http } = creer();
    let statut = 0;
    auth.connecter('medecin', 'faux').subscribe({ error: (e) => (statut = e.status) });
    http.expectOne('/auth/login').flush(
      { type: '/problemes/identifiants-invalides', status: 401 },
      { status: 401, statusText: 'Unauthorized' },
    );
    expect(statut).toBe(401);
    expect(auth.connecte()).toBe(false);
    expect(sessionStorage.getItem(CLE_SESSION)).toBeNull();
  });

  it('restaure une session encore valide après un rechargement de la page', () => {
    sessionStorage.setItem(
      CLE_SESSION,
      JSON.stringify({ jeton: 'j', expireLe: Date.now() + 60_000, sujet: 'TN-0012345', role: 'PATIENT' }),
    );
    const { auth } = creer();
    expect(auth.role()).toBe('PATIENT');
    expect(auth.jeton()).toBe('j');
  });

  it('ignore et efface une session expirée ou illisible', () => {
    sessionStorage.setItem(
      CLE_SESSION,
      JSON.stringify({ jeton: 'j', expireLe: Date.now() - 1, sujet: 'TN-0012345', role: 'PATIENT' }),
    );
    expect(creer().auth.connecte()).toBe(false);
    expect(sessionStorage.getItem(CLE_SESSION)).toBeNull();

    TestBed.resetTestingModule();
    sessionStorage.setItem(CLE_SESSION, '{"jeton":"j","role":"PIRATE"}');
    expect(creer().auth.connecte()).toBe(false);
  });

  it("ferme la session à l'instant expireLe et renvoie vers /connexion", () => {
    vi.useFakeTimers();
    const { auth, http, router } = creer();
    const navigation = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    auth.connecter('medecin', 'medecin_dev').subscribe();
    http.expectOne('/auth/login').flush({ ...REPONSE_MEDECIN, expireLe: new Date(Date.now() + 5_000).toISOString() });
    expect(auth.connecte()).toBe(true);

    vi.advanceTimersByTime(5_000);

    expect(auth.connecte()).toBe(false);
    expect(auth.jeton()).toBeNull();
    expect(sessionStorage.getItem(CLE_SESSION)).toBeNull();
    expect(navigation).toHaveBeenCalledWith('/connexion');
  });

  it('se déconnecte sur demande', () => {
    const { auth, http, router } = creer();
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    auth.connecter('medecin', 'medecin_dev').subscribe();
    http.expectOne('/auth/login').flush(REPONSE_MEDECIN);
    auth.deconnecter();
    expect(auth.connecte()).toBe(false);
    expect(sessionStorage.getItem(CLE_SESSION)).toBeNull();
  });
});
