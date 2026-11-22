import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DossierPatient, PatientResume } from '../../core/api/tableau-de-bord';
import { FichePatient } from './fiche-patient';
import { TableauDeBord } from './tableau-de-bord';

const MANSOURI: PatientResume = {
  id: 'TN-0012345',
  nom: 'Mansouri',
  prenom: 'Hédi',
  pathologie: 'Insuffisance cardiaque',
  niveauRisque: 'ELEVE',
  service: { code: 'CARDIO', libelle: 'Cardiologie' },
  medecinTraitant: { id: 'MED-001', nom: 'Ben Salah', prenom: 'Amine' },
  couverture: { statut: 'OUVERTS' },
  alertesRecentes: [
    { id: 'ALR-000001', niveau: 'CRITIQUE', message: 'Fréquence cardiaque 165 bpm > 140 bpm', horodatage: '2026-11-22T10:15:00+01:00' },
  ],
};

const DOSSIER: DossierPatient = {
  id: 'TN-0012345',
  nom: 'Mansouri',
  prenom: 'Hédi',
  dateNaissance: '1952-03-14',
  sexe: 'M',
  telephone: '+216 20 000 001',
  pathologie: 'Insuffisance cardiaque',
  niveauRisque: 'ELEVE',
  service: { code: 'CARDIO', libelle: 'Cardiologie' },
  medecinTraitant: { id: 'MED-001', nom: 'Ben Salah', prenom: 'Amine', specialite: 'Cardiologue' },
  couverture: {
    statut: 'OUVERTS',
    codeVignette: 'VIG-2026-100001',
    tauxRemboursement: 80,
    dateFin: '2026-12-31',
    misAJourLe: '2026-11-22T10:15:02+01:00',
  },
  dernieresConstantes: [
    { type: 'FREQUENCE_CARDIAQUE', valeur: 165, horodatage: '2026-11-22T10:15:00+01:00' },
    { type: 'SPO2', valeur: 96, horodatage: '2026-11-22T10:15:00+01:00' },
  ],
  alertesRecentes: [
    {
      id: 'ALR-000001',
      niveau: 'CRITIQUE',
      type: 'FREQUENCE_CARDIAQUE',
      valeur: 165,
      seuil: 140,
      message: 'Fréquence cardiaque 165 bpm > 140 bpm',
      horodatage: '2026-11-22T10:15:00+01:00',
    },
  ],
  traitementsEnCours: [
    {
      id: 'TRT-001',
      medicament: 'Bisoprolol',
      dosage: '5 mg',
      posologie: '1 comprimé le matin',
      dateDebut: '2026-02-10',
      dateFin: null,
      prescripteur: { id: 'MED-001', nom: 'Ben Salah', prenom: 'Amine', specialite: 'Cardiologue' },
    },
  ],
  fichesUrgence: [
    {
      id: 'FIC-000001',
      alerte: { id: 'ALR-000001', niveau: 'CRITIQUE', message: 'Fréquence cardiaque 165 bpm > 140 bpm', horodatage: '2026-11-22T10:15:00+01:00' },
      statutPriseEnCharge: 'PRISE_EN_CHARGE',
      action: 'CONSULTATION_URGENCE',
      identifiantRendezVous: 'RDV-000001',
      dateRendezVous: '2026-11-22T10:30:00+01:00',
      commentaire: null,
      creeeLe: '2026-11-22T10:15:03+01:00',
    },
  ],
};

function preparer() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  return TestBed.inject(HttpTestingController);
}

/** Requête GraphQL en attente dont la query contient `nom`. */
function gql(http: HttpTestingController, nom: string): TestRequest {
  return http.expectOne((r) => r.url === '/graphql' && (r.body as { query: string }).query.includes(nom));
}

afterEach(() => TestBed.inject(HttpTestingController).verify());

describe('Tableau de bord médecin (P4.4)', () => {
  it('liste les patients avec service, risque, couverture et dernière alerte', async () => {
    const http = preparer();
    const fixture = TestBed.createComponent(TableauDeBord);
    gql(http, 'services {').flush({ data: { services: [{ code: 'CARDIO', libelle: 'Cardiologie' }] } });
    const req = gql(http, 'patients(filtres');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.variables).toEqual({ filtres: {} });
    req.flush({ data: { patients: [MANSOURI] } });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const ligne = el.querySelector('[data-patient="TN-0012345"]')!;
    expect(ligne.textContent).toContain('Hédi Mansouri');
    expect(ligne.textContent).toContain('Cardiologie');
    expect(ligne.textContent).toContain('Élevé');
    expect(ligne.textContent).toContain('Droits ouverts');
    expect(ligne.textContent).toContain('Fréquence cardiaque 165 bpm');
    expect(ligne.querySelector('a')?.getAttribute('href')).toBe('/patients/TN-0012345');
  });

  it('envoie les filtres combinés du contrat (service, pathologie, niveaux de risque)', async () => {
    const http = preparer();
    const fixture = TestBed.createComponent(TableauDeBord);
    gql(http, 'services {').flush({ data: { services: [] } });
    gql(http, 'patients(filtres').flush({ data: { patients: [] } });
    const page = fixture.componentInstance as unknown as {
      serviceCode: string;
      pathologie: string;
      risques: Record<string, boolean>;
      rechercher(): void;
    };
    page.serviceCode = 'CARDIO';
    page.pathologie = ' cardiaque ';
    page.risques = { ELEVE: true, MOYEN: true, FAIBLE: false };
    page.rechercher();
    expect(gql(http, 'patients(filtres').request.body.variables).toEqual({
      filtres: { serviceCode: 'CARDIO', pathologie: 'cardiaque', niveauRisque: ['ELEVE', 'MOYEN'] },
    });
  });

  it('affiche le message des erreurs GraphQL', async () => {
    const http = preparer();
    const fixture = TestBed.createComponent(TableauDeBord);
    gql(http, 'services {').flush({ data: { services: [] } });
    gql(http, 'patients(filtres').flush({ errors: [{ message: 'Service indisponible' }] });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain('Service indisponible');
  });
});

describe('Fiche patient (P4.4)', () => {
  async function ouvrir(dossier: DossierPatient | null) {
    const http = preparer();
    const fixture = TestBed.createComponent(FichePatient);
    fixture.componentRef.setInput('id', 'TN-0012345');
    fixture.detectChanges();
    const req = gql(http, 'query Dossier');
    expect(req.request.body.variables).toEqual({ id: 'TN-0012345' });
    req.flush({ data: { patient: dossier } });
    const hist = gql(http, 'historiqueSoins(');
    expect(hist.request.body.variables.id).toBe('TN-0012345');
    expect(hist.request.body.variables.depuis).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    hist.flush({
      data: {
        patient: dossier && {
          historiqueSoins: {
            depuis: hist.request.body.variables.depuis,
            jusqua: hist.request.body.variables.jusqua,
            nombreAlertes: 1,
            alertesParNiveau: [
              { niveau: 'INFO', nombre: 0 },
              { niveau: 'URGENT', nombre: 0 },
              { niveau: 'CRITIQUE', nombre: 1 },
            ],
            nombreFichesUrgence: 1,
            nombreAmbulances: 0,
            traitements: [{ id: 'TRT-001' }, { id: 'TRT-002' }],
          },
        },
      },
    });
    await fixture.whenStable();
    return { http, fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('affiche le dossier consolidé en une requête : profil, couverture, constantes, fiches, alertes, traitements', async () => {
    const { el } = await ouvrir(DOSSIER);
    expect(el.querySelector('h1')?.textContent).toContain('Hédi Mansouri');
    expect(el.querySelector('[data-couverture]')?.textContent).toContain('Droits ouverts');
    expect(el.querySelector('[data-couverture]')?.textContent).toContain('80 %');
    expect(el.querySelector('[data-constantes]')?.textContent).toContain('165 bpm');
    expect(el.querySelector('[data-constantes]')?.textContent).toContain('96 %');
    expect(el.querySelector('[data-fiche="FIC-000001"]')?.textContent).toContain('RDV-000001');
    expect(el.querySelector('[data-alerte="ALR-000001"]')?.textContent).toContain('Critique');
    expect(el.textContent).toContain('Bisoprolol');
    expect(el.querySelector('[data-historique]')?.textContent).toContain('Critique 1');
  });

  it('indique un patient inconnu (patient = null)', async () => {
    const { el } = await ouvrir(null);
    expect(el.querySelector('[role=alert]')?.textContent).toContain('Aucun patient TN-0012345');
  });
});
