import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../../app.routes';
import { FABRIQUE_EVENT_SOURCE } from '../api/alertes-direct';
import { CLE_SESSION } from './auth.service';
import { Role } from './session';

/** Les espaces médecin ouvrent le flux SSE : EventSource simulé (absent de jsdom). */
const FAUX_SSE = {
  provide: FABRIQUE_EVENT_SOURCE,
  useValue: () => ({ addEventListener: () => undefined, close: () => undefined }) as unknown as EventSource,
};

async function naviguer(url: string, role: Role | null): Promise<string> {
  sessionStorage.clear();
  if (role) {
    sessionStorage.setItem(
      CLE_SESSION,
      JSON.stringify({ jeton: 'j', expireLe: Date.now() + 60_000, sujet: 'x', role }),
    );
  }
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter(routes), FAUX_SSE] });
  const harnais = await RouterTestingHarness.create();
  await harnais.navigateByUrl(url);
  return TestBed.inject(Router).url;
}

describe('Routes et rôles (D48)', () => {
  it('sans session : toute page renvoie vers /connexion', async () => {
    for (const url of ['/', '/patient', '/medecin', '/admin', '/inconnue']) {
      expect(await naviguer(url, null)).toBe('/connexion');
    }
  });

  it('la racine mène à la page du rôle connecté', async () => {
    expect(await naviguer('/', 'PATIENT')).toBe('/patient/rendez-vous');
    expect(await naviguer('/', 'MEDECIN')).toBe('/medecin');
    expect(await naviguer('/', 'ADMIN')).toBe('/admin');
  });

  it('chaque rôle accède à ses pages', async () => {
    expect(await naviguer('/patient', 'PATIENT')).toBe('/patient/rendez-vous');
    expect(await naviguer('/medecin', 'MEDECIN')).toBe('/medecin');
    expect(await naviguer('/admin', 'ADMIN')).toBe('/admin');
    expect(await naviguer('/medecin', 'ADMIN')).toBe('/medecin');
  });

  it('une page interdite renvoie vers l’accueil du rôle', async () => {
    expect(await naviguer('/medecin', 'PATIENT')).toBe('/patient/rendez-vous');
    expect(await naviguer('/admin', 'PATIENT')).toBe('/patient/rendez-vous');
    expect(await naviguer('/patient', 'MEDECIN')).toBe('/medecin');
    expect(await naviguer('/admin', 'MEDECIN')).toBe('/medecin');
    expect(await naviguer('/patient', 'ADMIN')).toBe('/admin');
  });

  it('un utilisateur connecté ne revoit pas la page de connexion', async () => {
    expect(await naviguer('/connexion', 'MEDECIN')).toBe('/medecin');
    expect(await naviguer('/connexion', null)).toBe('/connexion');
  });

  it('une session expirée est traitée comme une absence de session', async () => {
    sessionStorage.setItem(CLE_SESSION, JSON.stringify({ jeton: 'j', expireLe: Date.now() - 1, sujet: 'x', role: 'ADMIN' }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter(routes), FAUX_SSE] });
    const harnais = await RouterTestingHarness.create();
    await harnais.navigateByUrl('/admin');
    expect(TestBed.inject(Router).url).toBe('/connexion');
  });
});
