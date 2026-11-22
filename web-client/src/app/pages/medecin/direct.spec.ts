import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService, CLE_SESSION } from '../../core/auth/auth.service';
import {
  AlertesDirectService,
  EvenementAlerte,
  EvenementPriseEnCharge,
  FABRIQUE_EVENT_SOURCE,
} from '../../core/api/alertes-direct';
import { AlertesEnDirect } from './alertes-en-direct';
import { FichePatient } from './fiche-patient';

/** EventSource simulé : le test pousse les événements SSE à la main. */
class FauxEventSource {
  static derniere: FauxEventSource | null = null;
  readyState = 0;
  fermee = false;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  private ecouteurs = new Map<string, ((e: MessageEvent<string>) => void)[]>();

  constructor(readonly url: string) {
    FauxEventSource.derniere = this;
  }
  addEventListener(type: string, f: (e: MessageEvent<string>) => void): void {
    this.ecouteurs.set(type, [...(this.ecouteurs.get(type) ?? []), f]);
  }
  close(): void {
    this.fermee = true;
    this.readyState = 2;
  }
  ouvrir(): void {
    this.readyState = 1;
    this.onopen?.();
  }
  emettre(type: string, donnees: unknown): void {
    for (const f of this.ecouteurs.get(type) ?? []) f(new MessageEvent(type, { data: JSON.stringify(donnees) }));
  }
}

// Valeurs du contrat contracts/sse/README.md
const ALERTE: EvenementAlerte = {
  id: 'ALR-000001',
  patientId: 'TN-0012345',
  niveau: 'CRITIQUE',
  type: 'FREQUENCE_CARDIAQUE',
  valeur: 168,
  seuil: 140,
  message: 'Fréquence cardiaque 168 bpm > 140 bpm',
  horodatage: '2026-11-22T17:20:00+01:00',
};
const PEC: EvenementPriseEnCharge = {
  alerteId: 'ALR-000001',
  patientId: 'TN-0012345',
  etat: 'TERMINE',
  aRegulariser: false,
  rendezVousId: 'RDV-000001',
  debutRendezVous: '2026-11-22T17:30:00+01:00',
  ficheId: 'FIC-000001',
  interventionId: null,
  arriveeEstimee: null,
  horodatage: '2026-11-22T17:20:01+01:00',
};

function preparer(connecte = true) {
  sessionStorage.clear();
  if (connecte) {
    sessionStorage.setItem(
      CLE_SESSION,
      JSON.stringify({ jeton: 'abc.def', expireLe: Date.now() + 60_000, sujet: 'MED-001', role: 'MEDECIN' }),
    );
  }
  FauxEventSource.derniere = null;
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: FABRIQUE_EVENT_SOURCE, useValue: (url: string) => new FauxEventSource(url) as unknown as EventSource },
    ],
  });
  vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  return { direct: TestBed.inject(AlertesDirectService), http: TestBed.inject(HttpTestingController) };
}

afterEach(() => {
  TestBed.inject(HttpTestingController).verify();
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('Flux SSE des alertes (P4.5)', () => {
  it("s'abonne à /events avec le jeton en access_token (seul moyen pour EventSource, D44)", () => {
    const { direct } = preparer();
    direct.ouvrir();
    const es = FauxEventSource.derniere!;
    expect(es.url).toBe('/events?access_token=abc.def');
    expect(direct.etat()).toBe('connexion');
    es.ouvrir();
    expect(direct.etat()).toBe('ouvert');
    direct.ouvrir();
    expect(FauxEventSource.derniere).toBe(es);
  });

  it("n'ouvre rien sans session", () => {
    const { direct } = preparer(false);
    direct.ouvrir();
    expect(FauxEventSource.derniere).toBeNull();
  });

  it("ajoute l'alerte en tête puis la complète par sa prise en charge", () => {
    const { direct } = preparer();
    direct.ouvrir();
    const es = FauxEventSource.derniere!;
    const recus: string[] = [];
    direct.evenements.subscribe((e) => recus.push(e.type));

    es.emettre('alerte', ALERTE);
    expect(direct.alertes()[0].alerte.id).toBe('ALR-000001');
    expect(direct.alertes()[0].priseEnCharge).toBeNull();

    es.emettre('prise-en-charge', PEC);
    expect(direct.alertes()[0].priseEnCharge?.ficheId).toBe('FIC-000001');
    expect(recus).toEqual(['alerte', 'prise-en-charge']);
  });

  it('garde au plus 20 alertes, les plus récentes en tête', () => {
    const { direct } = preparer();
    direct.ouvrir();
    for (let i = 1; i <= 25; i++) {
      FauxEventSource.derniere!.emettre('alerte', { ...ALERTE, id: `ALR-${String(i).padStart(6, '0')}` });
    }
    expect(direct.alertes().length).toBe(20);
    expect(direct.alertes()[0].alerte.id).toBe('ALR-000025');
  });

  it('ferme le flux et oublie les alertes à la déconnexion', () => {
    const { direct } = preparer();
    direct.ouvrir();
    const es = FauxEventSource.derniere!;
    es.emettre('alerte', ALERTE);
    TestBed.inject(AuthService).deconnecter();
    TestBed.tick();
    expect(es.fermee).toBe(true);
    expect(direct.alertes()).toEqual([]);
    expect(direct.etat()).toBe('ferme');
  });

  it('signale un flux refusé (connexion fermée définitivement)', () => {
    const { direct } = preparer();
    direct.ouvrir();
    const es = FauxEventSource.derniere!;
    es.readyState = 2;
    es.onerror?.();
    expect(direct.etat()).toBe('erreur');
  });
});

describe('Panneau « Alertes en direct » (P4.5)', () => {
  it("affiche l'alerte avec le nom du patient puis le résultat de la prise en charge, sans rechargement", async () => {
    const { direct, http } = preparer();
    direct.ouvrir();
    const fixture = TestBed.createComponent(AlertesEnDirect);
    http
      .expectOne((r) => r.url === '/graphql')
      .flush({ data: { patients: [{ id: 'TN-0012345', prenom: 'Hédi', nom: 'Mansouri' }] } });
    const es = FauxEventSource.derniere!;
    es.ouvrir();
    es.emettre('alerte', ALERTE);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const ligne = el.querySelector('[data-direct="ALR-000001"]')!;
    expect(ligne.textContent).toContain('Critique');
    expect(ligne.textContent).toContain('Hédi Mansouri (TN-0012345)');
    expect(ligne.textContent).toContain('Fréquence cardiaque 168 bpm > 140 bpm');
    expect(ligne.textContent).toContain('Prise en charge en cours');
    expect(el.textContent).toContain('En direct');

    es.emettre('prise-en-charge', PEC);
    await fixture.whenStable();
    expect(ligne.textContent).toContain('Fiche FIC-000001 · rendez-vous RDV-000001 à 17:30');

    es.emettre('alerte', { ...ALERTE, id: 'ALR-000002' });
    // exemple du contrat SSE (P5.2) : aucun créneau, ambulance envoyée
    es.emettre('prise-en-charge', {
      ...PEC,
      alerteId: 'ALR-000002',
      etat: 'AMBULANCE',
      aRegulariser: true,
      rendezVousId: null,
      debutRendezVous: null,
      ficheId: 'FIC-000002',
      interventionId: 'AMB-000001',
      arriveeEstimee: '2026-11-22T23:41:00+01:00',
    });
    await fixture.whenStable();
    expect(el.querySelector('[data-direct="ALR-000002"]')?.textContent).toContain(
      'Fiche FIC-000002 · ambulance AMB-000001 (arrivée 23:41) · à régularise',
    );
  });
});

describe('Fiche patient mise à jour en direct (P4.5)', () => {
  it('relit le dossier quand un événement concerne ce patient, pas pour un autre', async () => {
    vi.useFakeTimers();
    const { direct, http } = preparer();
    direct.ouvrir();
    const fixture = TestBed.createComponent(FichePatient);
    fixture.componentRef.setInput('id', 'TN-0012345');
    fixture.detectChanges();
    const repondre = () => {
      for (const r of http.match((r) => r.url === '/graphql')) {
        r.flush({ data: { patient: null } });
      }
    };
    repondre();

    FauxEventSource.derniere!.emettre('alerte', { ...ALERTE, patientId: 'TN-0023456' });
    vi.advanceTimersByTime(400);
    expect(http.match((r) => r.url === '/graphql').length).toBe(0);

    FauxEventSource.derniere!.emettre('alerte', ALERTE);
    FauxEventSource.derniere!.emettre('prise-en-charge', PEC);
    vi.advanceTimersByTime(400);
    const relectures = http.match((r) => r.url === '/graphql');
    expect(relectures.map((r) => (r.request.body.query.includes('query Dossier') ? 'dossier' : 'historique'))).toEqual([
      'dossier',
      'historique',
    ]);
    relectures.forEach((r) => r.flush({ data: { patient: null } }));
  });
});
