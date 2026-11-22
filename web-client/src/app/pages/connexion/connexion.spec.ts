import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Connexion } from './connexion';

describe('Page de connexion', () => {
  let fixture: ComponentFixture<Connexion>;
  let http: HttpTestingController;
  let navigation: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Connexion],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true) as never;
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Connexion);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function saisir(identifiant: string, motDePasse: string): void {
    const el = fixture.nativeElement as HTMLElement;
    const champ = (id: string) => el.querySelector<HTMLInputElement>(`#${id}`)!;
    champ('identifiant').value = identifiant;
    champ('identifiant').dispatchEvent(new Event('input'));
    champ('motDePasse').value = motDePasse;
    champ('motDePasse').dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    fixture.detectChanges();
  }

  it("redirige vers l'accueil du rôle après une connexion réussie", () => {
    saisir('patient', 'patient_dev');
    http.expectOne('/auth/login').flush({
      jeton: 'j',
      type: 'Bearer',
      expireLe: new Date(Date.now() + 3_600_000).toISOString(),
      sujet: 'TN-0012345',
      role: 'PATIENT',
    });
    expect(navigation).toHaveBeenCalledWith('/patient');
  });

  it('affiche un message clair si les identifiants sont refusés', async () => {
    saisir('patient', 'faux');
    http.expectOne('/auth/login').flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain(
      'Identifiant ou mot de passe incorrect',
    );
    expect(navigation).not.toHaveBeenCalled();
  });

  it('signale une gateway injoignable', async () => {
    saisir('patient', 'patient_dev');
    http.expectOne('/auth/login').error(new ProgressEvent('error'), { status: 0 });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain('injoignable');
  });

  it("n'appelle pas la gateway si un champ est vide", async () => {
    saisir('', '');
    http.expectNone('/auth/login');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Identifiant obligatoire');
  });
});
