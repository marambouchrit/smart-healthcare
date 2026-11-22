import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { CLE_SESSION } from '../../core/auth/auth.service';
import { Ordonnance, ProfilPatient } from '../../core/api/portail';
import { OrdonnancesPage } from './ordonnances';
import { ProfilPage } from './profil';

const PROFIL: ProfilPatient = {
  id: 'TN-0012345',
  nom: 'Mansouri',
  prenom: 'Hédi',
  dateNaissance: '1952-03-14',
  sexe: 'M',
  telephone: '+216 20 000 001',
  email: 'h.mansouri@mail.example',
  adresse: { rue: '12 rue de Marseille', ville: 'Tunis', codePostal: '1000' },
  medecinTraitantId: 'MED-001',
};

const ORDONNANCE: Ordonnance = {
  id: 'ORD-000004',
  patientId: 'TN-0012345',
  medecinId: 'MED-001',
  date: '2026-02-10',
  lignes: [
    {
      traitementId: 'TRT-001',
      medicament: 'Bisoprolol',
      dosage: '5 mg',
      posologie: '1 comprimé le matin',
      dateDebut: '2026-02-10',
      dateFin: null,
      statut: 'EN_COURS',
    },
  ],
};

async function monter<T>(composant: Type<T>) {
  sessionStorage.setItem(
    CLE_SESSION,
    JSON.stringify({ jeton: 'j', expireLe: Date.now() + 60_000, sujet: 'TN-0012345', role: 'PATIENT' }),
  );
  await TestBed.configureTestingModule({
    imports: [composant],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  }).compileComponents();
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(composant);
  http.expectOne('seed/medecins.json').flush([
    { id: 'MED-001', nom: 'Ben Salah', prenom: 'Amine', specialite: 'Cardiologue', serviceCode: 'CARDIO' },
  ]);
  http.expectOne('seed/services-hospitaliers.json').flush([]);
  http.expectOne('seed/lettres-cles.json').flush([]);
  const rendre = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
  };
  return { fixture, http, el: fixture.nativeElement as HTMLElement, rendre };
}

afterEach(() => {
  TestBed.inject(HttpTestingController).verify();
  sessionStorage.clear();
});

describe('Espace patient — ordonnances (P4.3)', () => {
  it('affiche les ordonnances du patient du jeton avec leurs lignes', async () => {
    const { http, el, rendre } = await monter(OrdonnancesPage);
    http.expectOne('/api/patients/TN-0012345/ordonnances').flush([ORDONNANCE]);
    await rendre();
    expect(el.querySelector('[data-ordonnance="ORD-000004"]')).not.toBeNull();
    expect(el.textContent).toContain('Dr Amine Ben Salah');
    expect(el.textContent).toContain('Bisoprolol');
    expect(el.textContent).toContain('1 comprimé le matin');
    expect(el.textContent).toContain('En cours');
    expect(el.textContent).toContain('10 février 2026');
  });

  it('filtre par statut (paramètre statut du contrat)', async () => {
    const { http, el, rendre } = await monter(OrdonnancesPage);
    http.expectOne('/api/patients/TN-0012345/ordonnances').flush([ORDONNANCE]);
    await rendre();
    const select = el.querySelector<HTMLSelectElement>('select[name=statut]')!;
    select.value = 'TERMINE';
    select.dispatchEvent(new Event('change'));
    http.expectOne('/api/patients/TN-0012345/ordonnances?statut=TERMINE').flush([]);
    await rendre();
    expect(el.textContent).toContain('Aucune ordonnance.');
  });
});

describe('Espace patient — profil (P4.3)', () => {
  async function monterProfil() {
    const m = await monter(ProfilPage);
    m.http.expectOne('/api/patients/TN-0012345').flush(PROFIL);
    await m.rendre();
    const champ = (nom: string) => m.el.querySelector<HTMLInputElement>(`input[formControlName=${nom}]`)!;
    const saisir = (nom: string, valeur: string) => {
      champ(nom).value = valeur;
      champ(nom).dispatchEvent(new Event('input'));
      champ(nom).dispatchEvent(new Event('blur'));
    };
    const enregistrer = () => m.el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    return { ...m, champ, saisir, enregistrer };
  }

  it("affiche l'identité et pré-remplit les coordonnées", async () => {
    const { el, champ } = await monterProfil();
    expect(el.querySelector('[data-nom]')?.textContent).toContain('Hédi Mansouri');
    expect(el.textContent).toContain('14 mars 1952');
    expect(el.textContent).toContain('Dr Amine Ben Salah');
    expect(champ('telephone').value).toBe('+216 20 000 001');
    expect(champ('codePostal').value).toBe('1000');
  });

  it("n'envoie que les champs modifiés (PATCH), l'adresse toujours complète", async () => {
    const { http, el, saisir, enregistrer, rendre } = await monterProfil();
    saisir('telephone', '+216 98 765 432');
    saisir('ville', 'Ariana');
    saisir('codePostal', '2080');
    await rendre();
    enregistrer();
    const req = http.expectOne('/api/patients/TN-0012345');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({
      telephone: '+216 98 765 432',
      adresse: { rue: '12 rue de Marseille', ville: 'Ariana', codePostal: '2080' },
    });
    req.flush({ ...PROFIL, telephone: '+216 98 765 432', adresse: { ...PROFIL.adresse, ville: 'Ariana', codePostal: '2080' } });
    await rendre();
    expect(el.querySelector('.bandeau.ok')?.textContent).toContain('Coordonnées mises à jour');
  });

  it('bloque une saisie non conforme au contrat (téléphone, code postal)', async () => {
    const { http, el, saisir, enregistrer, rendre } = await monterProfil();
    saisir('telephone', 'abc');
    saisir('codePostal', '12');
    await rendre();
    enregistrer();
    http.expectNone('/api/patients/TN-0012345');
    await rendre();
    expect(el.textContent).toContain('8 à 20 chiffres');
    expect(el.textContent).toContain('code postal à 4 chiffres');
  });

  it('affiche le detail renvoyé par le service en cas de refus (400)', async () => {
    const { http, el, saisir, enregistrer, rendre } = await monterProfil();
    saisir('email', 'hedi.mansouri@mail.example');
    await rendre();
    enregistrer();
    http.expectOne('/api/patients/TN-0012345').flush(
      { type: '/problemes/requete-invalide', title: 'Requête invalide', status: 400, detail: 'Le champ email est invalide.' },
      { status: 400, statusText: 'Bad Request' },
    );
    await rendre();
    expect(el.querySelector('.bandeau.erreur')?.textContent).toContain('Le champ email est invalide.');
  });
});
