import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DossierSoapService, ErreurSoap } from '../../core/api/dossier-soap';
import { Administration } from './administration';

// Réponses réelles du service SOAP (relevées via la gateway, P4.6).
const DROITS_OUVERTS = `<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><verifierDroitsCNAMResponse xmlns="http://hopital.tn/dossier"><statut>OUVERTS</statut><priseEnCharge>true</priseEnCharge><couverture><identifiantPatient>TN-0012345</identifiantPatient><codeVignette>VIG-2026-100001</codeVignette><filiere>PUBLIQUE</filiere><dateDebut>2026-01-01</dateDebut><dateFin>2026-12-31</dateFin><tauxRemboursement>80</tauxRemboursement></couverture></verifierDroitsCNAMResponse></soap:Body></soap:Envelope>`;
const NON_AFFILIE = `<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><verifierDroitsCNAMResponse xmlns="http://hopital.tn/dossier"><statut>NON_AFFILIE</statut><priseEnCharge>false</priseEnCharge><motif>Aucune affiliation CNAM pour ce patient</motif></verifierDroitsCNAMResponse></soap:Body></soap:Envelope>`;
const HISTORIQUE = `<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><consulterHistoriqueResponse xmlns="http://hopital.tn/dossier"><historique><identifiantPatient>TN-0012345</identifiantPatient><dateCertification>2026-11-22T18:17:24+01:00</dateCertification><entree><date>2026-02-10</date><type>CONSULTATION</type><description>Consultation et prescription : Bisoprolol 5 mg, Furosémide 40 mg</description><identifiantMedecin>MED-001</identifiantMedecin></entree></historique></consulterHistoriqueResponse></soap:Body></soap:Envelope>`;
const FAULT_INCONNU = `<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><soap:Fault><faultcode>soap:Server</faultcode><faultstring>Aucun dossier pour l'identifiant TN-9999999</faultstring><detail><erreurMetier xmlns="http://hopital.tn/dossier"><code>PATIENT_INCONNU</code><message>Aucun dossier pour l'identifiant TN-9999999</message></erreurMetier></detail></soap:Fault></soap:Body></soap:Envelope>`;

function preparer() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  return { soap: TestBed.inject(DossierSoapService), http: TestBed.inject(HttpTestingController) };
}

afterEach(() => TestBed.inject(HttpTestingController).verify());

describe('Client SOAP Dossier & CNAM (P4.6)', () => {
  it('envoie une enveloppe SOAP 1.1 conforme au XSD avec le SOAPAction du WSDL', () => {
    const { soap, http } = preparer();
    soap.verifierDroitsCNAM('TN-0012345', 'C', '2026-11-22').subscribe();
    const req = http.expectOne('/ws/dossier');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Content-Type')).toBe('text/xml; charset=utf-8');
    expect(req.request.headers.get('SOAPAction')).toBe('"http://hopital.tn/dossier/verifierDroitsCNAM"');
    const doc = new DOMParser().parseFromString(req.request.body as string, 'text/xml');
    const op = doc.getElementsByTagNameNS('http://hopital.tn/dossier', 'verifierDroitsCNAM')[0];
    expect(op.parentElement?.localName).toBe('Body');
    expect(Array.from(op.children).map((c) => `${c.namespaceURI}|${c.localName}=${c.textContent}`)).toEqual([
      'http://hopital.tn/dossier|identifiantPatient=TN-0012345',
      'http://hopital.tn/dossier|lettreCle=C',
      'http://hopital.tn/dossier|datePrestation=2026-11-22',
    ]);
    req.flush(DROITS_OUVERTS);
  });

  it('lit verifierDroitsCNAMResponse (couverture) et consulterHistoriqueResponse (entrées)', () => {
    const { soap, http } = preparer();
    let droits: unknown;
    let historique: unknown;
    soap.verifierDroitsCNAM('TN-0012345', 'C', '2026-11-22').subscribe((e) => (droits = e.resultat));
    http.expectOne('/ws/dossier').flush(DROITS_OUVERTS);
    expect(droits).toEqual({
      statut: 'OUVERTS',
      priseEnCharge: true,
      couverture: {
        identifiantPatient: 'TN-0012345',
        codeVignette: 'VIG-2026-100001',
        filiere: 'PUBLIQUE',
        dateDebut: '2026-01-01',
        dateFin: '2026-12-31',
        tauxRemboursement: 80,
      },
      motif: null,
    });

    soap.consulterHistorique('TN-0012345', '2026-01-01').subscribe((e) => (historique = e.resultat));
    const req = http.expectOne('/ws/dossier');
    expect(req.request.headers.get('SOAPAction')).toBe('"http://hopital.tn/dossier/consulterHistorique"');
    expect(req.request.body).toContain('<d:dateDebut>2026-01-01</d:dateDebut>');
    expect(req.request.body).not.toContain('dateFin');
    req.flush(HISTORIQUE);
    expect(historique).toEqual({
      identifiantPatient: 'TN-0012345',
      dateCertification: '2026-11-22T18:17:24+01:00',
      entrees: [
        {
          date: '2026-02-10',
          type: 'CONSULTATION',
          description: 'Consultation et prescription : Bisoprolol 5 mg, Furosémide 40 mg',
          identifiantMedecin: 'MED-001',
          numeroFeuilleDeSoins: null,
        },
      ],
    });
  });

  it('transforme un SOAP Fault (HTTP 500) en ErreurSoap avec le code erreurMetier', () => {
    const { soap, http } = preparer();
    let erreur: unknown;
    soap.consulterHistorique('TN-9999999').subscribe({ error: (e) => (erreur = e) });
    http.expectOne('/ws/dossier').flush(FAULT_INCONNU, { status: 500, statusText: 'Server Error' });
    expect(erreur).toBeInstanceOf(ErreurSoap);
    expect((erreur as ErreurSoap).code).toBe('PATIENT_INCONNU');
    expect((erreur as ErreurSoap).message).toBe("Aucun dossier pour l'identifiant TN-9999999");
  });

  it('échappe les caractères XML des valeurs saisies', () => {
    const { soap, http } = preparer();
    soap.consulterHistorique('TN-<x>&').subscribe({ error: () => undefined });
    const req = http.expectOne('/ws/dossier');
    expect(req.request.body).toContain('<d:identifiantPatient>TN-&lt;x&gt;&amp;</d:identifiantPatient>');
    req.flush('', { status: 500, statusText: 'Server Error' });
  });
});

describe('Vue administrateur (P4.6)', () => {
  async function ouvrir() {
    const { http } = preparer();
    const fixture = TestBed.createComponent(Administration);
    http.expectOne('seed/medecins.json').flush([
      { id: 'MED-001', nom: 'Ben Salah', prenom: 'Amine', specialite: 'Cardiologue', serviceCode: 'CARDIO' },
    ]);
    http.expectOne('seed/services-hospitaliers.json').flush([]);
    http.expectOne('seed/lettres-cles.json').flush([{ code: 'C', libelle: 'Consultation de médecine générale' }]);
    http.expectOne('/graphql').flush({ data: { patients: [{ id: 'TN-0012345', prenom: 'Hédi', nom: 'Mansouri' }] } });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const page = fixture.componentInstance as unknown as { identifiant: string };
    const cliquer = (texte: string) =>
      Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === texte)!.click();
    return { http, fixture, el, page, cliquer };
  }

  it('affiche les droits ouverts avec vignette, validité, taux et les enveloppes SOAP', async () => {
    const { http, fixture, el, cliquer } = await ouvrir();
    cliquer('Vérifier');
    http.expectOne('/ws/dossier').flush(DROITS_OUVERTS);
    await fixture.whenStable();
    const bloc = el.querySelector('[data-droits]')!;
    expect(bloc.textContent).toContain('Droits ouverts');
    expect(bloc.textContent).toContain('VIG-2026-100001 (publique)');
    expect(bloc.textContent).toContain('80 %');
    expect(bloc.textContent).toContain('31 décembre 2026');
    expect(el.querySelector('details pre')?.textContent).toContain('<d:verifierDroitsCNAM>');
  });

  it('affiche un patient non affilié avec le motif renvoyé', async () => {
    const { http, fixture, el, page, cliquer } = await ouvrir();
    page.identifiant = 'TN-0089012';
    cliquer('Vérifier');
    http.expectOne('/ws/dossier').flush(NON_AFFILIE);
    await fixture.whenStable();
    expect(el.querySelector('[data-droits]')?.textContent).toContain('Non affilié');
    expect(el.querySelector('[data-droits]')?.textContent).toContain('Aucune affiliation CNAM pour ce patient');
  });

  it("affiche l'historique certifié avec le nom du médecin", async () => {
    const { http, fixture, el, cliquer } = await ouvrir();
    cliquer('Consulter');
    http.expectOne('/ws/dossier').flush(HISTORIQUE);
    await fixture.whenStable();
    const table = el.querySelector('[data-historique]')!;
    expect(table.textContent).toContain('10 février 2026');
    expect(table.textContent).toContain('Consultation');
    expect(table.textContent).toContain('Dr Amine Ben Salah');
  });

  it('affiche le code du SOAP Fault et refuse un identifiant mal formé sans appel', async () => {
    const { http, fixture, el, page, cliquer } = await ouvrir();
    page.identifiant = 'TN-9999999';
    cliquer('Consulter');
    http.expectOne('/ws/dossier').flush(FAULT_INCONNU, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(el.querySelector('[data-erreur-historique]')?.textContent).toContain('PATIENT_INCONNU');

    page.identifiant = 'TN-12';
    cliquer('Vérifier');
    http.expectNone('/ws/dossier');
    await fixture.whenStable();
    expect(el.textContent).toContain('TN- suivi de 7 chiffres');
  });
});
