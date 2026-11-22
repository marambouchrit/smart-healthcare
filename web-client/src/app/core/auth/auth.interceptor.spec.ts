import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { AuthService, CLE_SESSION } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controle: HttpTestingController;
  let auth: AuthService;

  function demarrer(sessionValide: boolean): void {
    sessionStorage.clear();
    if (sessionValide) {
      sessionStorage.setItem(
        CLE_SESSION,
        JSON.stringify({ jeton: 'abc.def.ghi', expireLe: Date.now() + 60_000, sujet: 'MED-001', role: 'MEDECIN' }),
      );
    }
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    http = TestBed.inject(HttpClient);
    controle = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  }

  afterEach(() => controle.verify());

  it('ajoute Authorization: Bearer aux appels vers la gateway', () => {
    demarrer(true);
    http.get('/api/creneaux').subscribe();
    http.post('/graphql', { query: '{ services { code } }' }).subscribe();
    expect(controle.expectOne('/api/creneaux').request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
    expect(controle.expectOne('/graphql').request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
  });

  it("n'envoie pas le jeton à /auth/login ni à une autre origine", () => {
    demarrer(true);
    http.post('/auth/login', {}).subscribe();
    http.get('https://exemple.org/api').subscribe();
    http.get('//exemple.org/api').subscribe();
    expect(controle.expectOne('/auth/login').request.headers.has('Authorization')).toBe(false);
    expect(controle.expectOne('https://exemple.org/api').request.headers.has('Authorization')).toBe(false);
    expect(controle.expectOne('//exemple.org/api').request.headers.has('Authorization')).toBe(false);
  });

  it("laisse passer la requête sans en-tête quand personne n'est connecté", () => {
    demarrer(false);
    http.get('/api/creneaux').subscribe({ error: () => undefined });
    expect(controle.expectOne('/api/creneaux').request.headers.has('Authorization')).toBe(false);
  });

  it('ferme la session si la gateway répond 401 et transmet l’erreur', () => {
    demarrer(true);
    let statut = 0;
    http.get('/api/creneaux').subscribe({ error: (e) => (statut = e.status) });
    controle.expectOne('/api/creneaux').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(statut).toBe(401);
    expect(auth.connecte()).toBe(false);
    expect(sessionStorage.getItem(CLE_SESSION)).toBeNull();
  });

  it('garde la session sur une autre erreur (404)', () => {
    demarrer(true);
    http.get('/api/patients/TN-9999999').subscribe({ error: () => undefined });
    controle.expectOne('/api/patients/TN-9999999').flush(null, { status: 404, statusText: 'Not Found' });
    expect(auth.connecte()).toBe(true);
  });
});
