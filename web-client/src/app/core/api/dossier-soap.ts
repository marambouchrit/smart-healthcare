import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';

/*
 * Client SOAP 1.1 du service Dossier & CNAM (contrat : contracts/soap/dossier.wsdl + dossier.xsd), via la gateway.
 * document/literal, namespace http://hopital.tn/dossier, elementFormDefault="qualified" : chaque élément
 * de la requête est dans le namespace. Le navigateur n'a pas de client SOAP : l'enveloppe est écrite à la
 * main et la réponse lue avec DOMParser (D50).
 */

export const NS_DOSSIER = 'http://hopital.tn/dossier';
const NS_ENVELOPPE = 'http://schemas.xmlsoap.org/soap/envelope/';
const URL_SERVICE = '/ws/dossier';

export type StatutDroits = 'OUVERTS' | 'EXPIRES' | 'NON_AFFILIE';
export type TypeEvenement = 'CONSULTATION' | 'ACTE' | 'HOSPITALISATION' | 'URGENCE';
export type CodeErreur = 'PATIENT_INCONNU' | 'VIGNETTE_INVALIDE' | 'DONNEES_INVALIDES';

export interface CouvertureSoap {
  identifiantPatient: string;
  codeVignette: string;
  filiere: string;
  dateDebut: string;
  dateFin: string;
  tauxRemboursement: number;
}

export interface ReponseDroits {
  statut: StatutDroits;
  priseEnCharge: boolean;
  couverture: CouvertureSoap | null;
  motif: string | null;
}

export interface EntreeHistorique {
  date: string;
  type: TypeEvenement;
  description: string;
  identifiantMedecin: string;
  numeroFeuilleDeSoins: string | null;
}

export interface Historique {
  identifiantPatient: string;
  dateCertification: string;
  entrees: EntreeHistorique[];
}

/** Échange brut, affiché dans la vue administrateur pour montrer le protocole. */
export interface EchangeSoap<T> {
  resultat: T;
  requete: string;
  reponse: string;
}

/** SOAP Fault : `erreurMetier` du contrat si présent, sinon faultstring. */
export class ErreurSoap extends Error {
  constructor(
    message: string,
    readonly code: CodeErreur | null,
    readonly requete: string,
    readonly reponse: string,
  ) {
    super(message);
  }
}

@Injectable({ providedIn: 'root' })
export class DossierSoapService {
  private readonly http = inject(HttpClient);

  verifierDroitsCNAM(identifiantPatient: string, lettreCle: string, datePrestation: string): Observable<EchangeSoap<ReponseDroits>> {
    const corps =
      `<d:verifierDroitsCNAM>` +
      element('identifiantPatient', identifiantPatient) +
      element('lettreCle', lettreCle) +
      element('datePrestation', datePrestation) +
      `</d:verifierDroitsCNAM>`;
    return this.appeler('verifierDroitsCNAM', corps, (rep) => {
      const r = premier(rep, 'verifierDroitsCNAMResponse')!;
      const c = premier(r, 'couverture');
      return {
        statut: texte(r, 'statut') as StatutDroits,
        priseEnCharge: texte(r, 'priseEnCharge') === 'true',
        couverture: c
          ? {
              identifiantPatient: texte(c, 'identifiantPatient')!,
              codeVignette: texte(c, 'codeVignette')!,
              filiere: texte(c, 'filiere')!,
              dateDebut: texte(c, 'dateDebut')!,
              dateFin: texte(c, 'dateFin')!,
              tauxRemboursement: Number(texte(c, 'tauxRemboursement')),
            }
          : null,
        motif: texte(r, 'motif'),
      };
    });
  }

  consulterHistorique(identifiantPatient: string, dateDebut?: string, dateFin?: string): Observable<EchangeSoap<Historique>> {
    const corps =
      `<d:consulterHistorique>` +
      element('identifiantPatient', identifiantPatient) +
      (dateDebut ? element('dateDebut', dateDebut) : '') +
      (dateFin ? element('dateFin', dateFin) : '') +
      `</d:consulterHistorique>`;
    return this.appeler('consulterHistorique', corps, (rep) => {
      const h = premier(rep, 'historique')!;
      return {
        identifiantPatient: texte(h, 'identifiantPatient')!,
        dateCertification: texte(h, 'dateCertification')!,
        entrees: Array.from(h.getElementsByTagNameNS(NS_DOSSIER, 'entree')).map((e) => ({
          date: texte(e, 'date')!,
          type: texte(e, 'type') as TypeEvenement,
          description: texte(e, 'description')!,
          identifiantMedecin: texte(e, 'identifiantMedecin')!,
          numeroFeuilleDeSoins: texte(e, 'numeroFeuilleDeSoins'),
        })),
      };
    });
  }

  private appeler<T>(operation: string, corps: string, lire: (doc: Document) => T): Observable<EchangeSoap<T>> {
    const requete =
      `<?xml version="1.0" encoding="UTF-8"?>\n` +
      `<soapenv:Envelope xmlns:soapenv="${NS_ENVELOPPE}" xmlns:d="${NS_DOSSIER}">` +
      `<soapenv:Header/><soapenv:Body>${corps}</soapenv:Body></soapenv:Envelope>`;
    const entetes = new HttpHeaders({
      'Content-Type': 'text/xml; charset=utf-8',
      SOAPAction: `"${NS_DOSSIER}/${operation}"`,
    });
    return this.http.post(URL_SERVICE, requete, { headers: entetes, responseType: 'text' }).pipe(
      map((reponse) => {
        const doc = analyser(reponse);
        const faute = lireFault(doc, requete, reponse);
        if (faute) throw faute;
        return { resultat: lire(doc), requete, reponse };
      }),
      catchError((e: unknown) => {
        // Un Fault SOAP 1.1 arrive en HTTP 500 avec l'enveloppe dans le corps.
        if (e instanceof HttpErrorResponse && typeof e.error === 'string' && e.error.includes('Fault')) {
          const faute = lireFault(analyser(e.error), requete, e.error);
          if (faute) return throwError(() => faute);
        }
        return throwError(() => e);
      }),
    );
  }
}

function element(nom: string, valeur: string): string {
  return `<d:${nom}>${echapper(valeur)}</d:${nom}>`;
}

function echapper(v: string): string {
  return v.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}

function analyser(xml: string): Document {
  return new DOMParser().parseFromString(xml, 'text/xml');
}

function premier(racine: Document | Element, nom: string): Element | null {
  return racine.getElementsByTagNameNS(NS_DOSSIER, nom)[0] ?? null;
}

/** Texte d'un enfant direct (évite de lire `identifiantPatient` d'un sous-élément). */
function texte(parent: Element, nom: string): string | null {
  for (const enfant of Array.from(parent.children)) {
    if (enfant.namespaceURI === NS_DOSSIER && enfant.localName === nom) return enfant.textContent;
  }
  return null;
}

function lireFault(doc: Document, requete: string, reponse: string): ErreurSoap | null {
  const fault = doc.getElementsByTagNameNS(NS_ENVELOPPE, 'Fault')[0];
  if (!fault) return null;
  const metier = premier(doc, 'erreurMetier');
  if (metier) {
    return new ErreurSoap(texte(metier, 'message') ?? 'Erreur métier', texte(metier, 'code') as CodeErreur, requete, reponse);
  }
  const faultstring = Array.from(fault.children).find((c) => c.localName === 'faultstring')?.textContent;
  return new ErreurSoap(faultstring || 'Erreur SOAP', null, requete, reponse);
}
