import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { CLE_SESSION } from '../../core/auth/auth.service';
import { Creneau, RendezVous } from '../../core/api/portail';
import { RendezVousPage } from './rendez-vous';

const DEMAIN = new Date(Date.now() + 86_400_000);
const iso = (d: Date, minutes = 0) => new Date(d.getTime() + minutes * 60_000).toISOString();

const CRENEAU_LIBRE: Creneau = {
  id: 'CRN-000101',
  medecinId: 'MED-001',
  serviceCode: 'CARDIO',
  debut: iso(DEMAIN),
  fin: iso(DEMAIN, 30),
  disponible: true,
};
const CRENEAU_URGENCES: Creneau = { ...CRENEAU_LIBRE, id: 'CRN-000900', medecinId: 'MED-004', serviceCode: 'URGENCES' };
const RDV: RendezVous = {
  id: 'RDV-000007',
  patientId: 'TN-0012345',
  creneau: { ...CRENEAU_LIBRE, id: 'CRN-000050', disponible: false },
  motif: 'Contrôle',
  statut: 'CONFIRME',
  urgence: false,
  creeLe: iso(new Date()),
};
const RDV_ANNULE: RendezVous = { ...RDV, id: 'RDV-000003', statut: 'ANNULE' };

describe('Espace patient — rendez-vous (P4.2)', () => {
  let fixture: ComponentFixture<RendezVousPage>;
  let http: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    sessionStorage.setItem(
      CLE_SESSION,
      JSON.stringify({ jeton: 'j', expireLe: Date.now() + 60_000, sujet: 'TN-0012345', role: 'PATIENT' }),
    );
    await TestBed.configureTestingModule({
      imports: [RendezVousPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RendezVousPage);
    el = fixture.nativeElement as HTMLElement;
    http.expectOne('seed/medecins.json').flush([
      { id: 'MED-001', nom: 'Ben Salah', prenom: 'Amine', specialite: 'Cardiologue', serviceCode: 'CARDIO' },
      { id: 'MED-004', nom: 'X', prenom: 'Y', specialite: 'Urgentiste', serviceCode: 'URGENCES' },
    ]);
    http.expectOne('seed/services-hospitaliers.json').flush([
      { code: 'CARDIO', libelle: 'Cardiologie' },
      { code: 'URGENCES', libelle: 'Urgences' },
    ]);
    http.expectOne('/api/rendez-vous?patientId=TN-0012345').flush([RDV, RDV_ANNULE]);
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  const bouton = (texte: string, racine: ParentNode = el) =>
    Array.from(racine.querySelectorAll('button')).find((b) => b.textContent?.trim() === texte)!;

  async function rendre(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function chercherEtChoisir(): Promise<void> {
    bouton('Voir les créneaux libres').click();
    const req = http.expectOne((r) => r.url === '/api/creneaux');
    expect(req.request.params.get('disponible')).toBe('true');
    expect(req.request.params.get('date')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    req.flush([CRENEAU_LIBRE, CRENEAU_URGENCES]);
    await rendre();
    el.querySelector<HTMLButtonElement>('[data-creneau="CRN-000101"]')!.click();
    await rendre();
  }

  it('liste les rendez-vous du patient du jeton : à venir et historique, avec le nom du médecin', () => {
    const lignes = Array.from(el.querySelectorAll('[data-id]')).map((l) => l.getAttribute('data-id'));
    expect(lignes).toEqual(['RDV-000007', 'RDV-000003']);
    expect(el.textContent).toContain('Dr Amine Ben Salah');
    expect(el.textContent).toContain('Cardiologie');
    expect(el.textContent).toContain('Annulé');
    expect(el.querySelector('option[value="URGENCES"]')).toBeNull();
  });

  it('prend un rendez-vous : POST avec le patient du jeton, le créneau et le motif', async () => {
    await chercherEtChoisir();
    expect(el.querySelector('[data-creneau="CRN-000900"]')).toBeNull();

    const motif = el.querySelector<HTMLInputElement>('input[name=motif]')!;
    motif.value = 'Douleur thoracique';
    motif.dispatchEvent(new Event('input'));
    bouton('Confirmer le rendez-vous').click();

    const req = http.expectOne('/api/rendez-vous');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ patientId: 'TN-0012345', creneauId: 'CRN-000101', motif: 'Douleur thoracique' });
    req.flush({ ...RDV, id: 'RDV-000008', creneau: CRENEAU_LIBRE });
    http.expectOne('/api/rendez-vous?patientId=TN-0012345').flush([RDV]);
    await rendre();
    expect(el.querySelector('.bandeau.ok')?.textContent).toContain('Rendez-vous confirmé : RDV-000008');
  });

  it('affiche le detail du Problem Details si le créneau vient d’être pris (409)', async () => {
    await chercherEtChoisir();
    bouton('Confirmer le rendez-vous').click();
    http.expectOne('/api/rendez-vous').flush(
      { type: '/problemes/creneau-indisponible', title: 'Créneau indisponible', status: 409, detail: 'Le créneau CRN-000101 est déjà réservé.' },
      { status: 409, statusText: 'Conflict' },
    );
    http.expectOne((r) => r.url === '/api/creneaux').flush([]);
    await rendre();
    expect(el.querySelector('.bandeau.erreur')?.textContent).toContain('Le créneau CRN-000101 est déjà réservé.');
  });

  it('change de créneau : PUT sur le rendez-vous avec le nouveau créneau', async () => {
    bouton('Changer de créneau').click();
    await rendre();
    expect(el.textContent).toContain('Changer le créneau du rendez-vous RDV-000007');
    await chercherEtChoisir();
    bouton('Déplacer le rendez-vous').click();

    const req = http.expectOne('/api/rendez-vous/RDV-000007');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ patientId: 'TN-0012345', creneauId: 'CRN-000101', motif: 'Contrôle' });
    req.flush({ ...RDV, creneau: CRENEAU_LIBRE });
    http.expectOne('/api/rendez-vous?patientId=TN-0012345').flush([]);
  });

  it('modifie le motif : PATCH {motif}', async () => {
    bouton('Modifier le motif').click();
    await rendre();
    const champ = el.querySelector<HTMLInputElement>('input[name=nouveauMotif]')!;
    champ.value = 'Bilan annuel';
    champ.dispatchEvent(new Event('input'));
    bouton('Enregistrer').click();

    const req = http.expectOne('/api/rendez-vous/RDV-000007');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ motif: 'Bilan annuel' });
    req.flush({ ...RDV, motif: 'Bilan annuel' });
    http.expectOne('/api/rendez-vous?patientId=TN-0012345').flush([]);
  });

  it('annule après confirmation : PATCH {statut: ANNULE}', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    bouton('Annuler', el.querySelector('[data-id="RDV-000007"]')!).click();
    const req = http.expectOne('/api/rendez-vous/RDV-000007');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ statut: 'ANNULE' });
    req.flush({ ...RDV, statut: 'ANNULE' });
    http.expectOne('/api/rendez-vous?patientId=TN-0012345').flush([{ ...RDV, statut: 'ANNULE' }]);
    await rendre();
    expect(el.querySelector('.bandeau.ok')?.textContent).toContain('annulé');
  });

  it("n'annule rien si le patient renonce", () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    bouton('Annuler', el.querySelector('[data-id="RDV-000007"]')!).click();
    http.expectNone('/api/rendez-vous/RDV-000007');
  });
});
