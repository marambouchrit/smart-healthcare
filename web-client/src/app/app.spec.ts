import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { CLE_SESSION } from './core/auth/auth.service';

async function afficher(): Promise<HTMLElement> {
  await TestBed.configureTestingModule({
    imports: [App],
    providers: [provideHttpClient(), provideRouter([])],
  }).compileComponents();
  const fixture = TestBed.createComponent(App);
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('App', () => {
  beforeEach(() => sessionStorage.clear());

  it('sans session : seule la marque est affichée', async () => {
    const el = await afficher();
    expect(el.querySelector('.marque')?.textContent).toContain('Smart Healthcare');
    expect(el.querySelector('nav')).toBeNull();
  });

  it("connecté : affiche le rôle, l'identifiant et les liens du rôle", async () => {
    sessionStorage.setItem(
      CLE_SESSION,
      JSON.stringify({ jeton: 'j', expireLe: Date.now() + 60_000, sujet: 'admin', role: 'ADMIN' }),
    );
    const el = await afficher();
    expect(el.querySelector('.utilisateur')?.textContent).toContain('Administrateur · admin');
    const liens = Array.from(el.querySelectorAll('nav a')).map((a) => a.getAttribute('href'));
    expect(liens).toEqual(['/admin', '/medecin']);
  });
});
