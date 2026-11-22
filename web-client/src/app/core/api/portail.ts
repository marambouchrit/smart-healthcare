import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/*
 * Client du portail REST (contrat : contracts/rest/portail-openapi.yaml), appelé via la gateway.
 * Les types reprennent les schémas du contrat, sans ajout.
 */

export type StatutRendezVous = 'CONFIRME' | 'ANNULE' | 'HONORE';
export type StatutTraitement = 'EN_COURS' | 'TERMINE';

export interface Creneau {
  id: string;
  medecinId: string;
  serviceCode: string;
  debut: string;
  fin: string;
  disponible: boolean;
}

export interface RendezVous {
  id: string;
  patientId: string;
  creneau: Creneau;
  motif?: string;
  statut: StatutRendezVous;
  urgence: boolean;
  creeLe: string;
}

export interface RendezVousCreation {
  patientId: string;
  creneauId: string;
  motif?: string;
}

export interface RendezVousModification {
  motif?: string;
  statut?: StatutRendezVous;
}

export interface Adresse {
  rue: string;
  ville: string;
  codePostal: string;
}

export interface ProfilPatient {
  id: string;
  nom: string;
  prenom: string;
  dateNaissance: string;
  sexe: 'M' | 'F';
  telephone?: string;
  email?: string;
  adresse: Adresse;
  medecinTraitantId?: string;
}

export interface ProfilModification {
  telephone?: string;
  email?: string;
  adresse?: Adresse;
}

export interface LignePrescription {
  traitementId: string;
  medicament: string;
  dosage: string;
  posologie: string;
  dateDebut: string;
  dateFin?: string | null;
  statut: StatutTraitement;
}

export interface Ordonnance {
  id: string;
  patientId: string;
  medecinId: string;
  date: string;
  lignes: LignePrescription[];
}

export interface FiltresCreneaux {
  medecinId?: string;
  serviceCode?: string;
  date?: string;
  disponible?: boolean;
}

function parametres(filtres: object): HttpParams {
  let p = new HttpParams();
  for (const [cle, valeur] of Object.entries(filtres)) {
    if (valeur !== undefined && valeur !== null && valeur !== '') p = p.set(cle, String(valeur));
  }
  return p;
}

@Injectable({ providedIn: 'root' })
export class PortailService {
  private readonly http = inject(HttpClient);

  listerCreneaux(filtres: FiltresCreneaux): Observable<Creneau[]> {
    return this.http.get<Creneau[]>('/api/creneaux', { params: parametres(filtres) });
  }

  listerRendezVous(filtres: { patientId?: string; medecinId?: string; date?: string }): Observable<RendezVous[]> {
    return this.http.get<RendezVous[]>('/api/rendez-vous', { params: parametres(filtres) });
  }

  creerRendezVous(corps: RendezVousCreation): Observable<RendezVous> {
    return this.http.post<RendezVous>('/api/rendez-vous', corps);
  }

  remplacerRendezVous(id: string, corps: RendezVousCreation): Observable<RendezVous> {
    return this.http.put<RendezVous>(`/api/rendez-vous/${encodeURIComponent(id)}`, corps);
  }

  modifierRendezVous(id: string, corps: RendezVousModification): Observable<RendezVous> {
    return this.http.patch<RendezVous>(`/api/rendez-vous/${encodeURIComponent(id)}`, corps);
  }

  obtenirProfil(patientId: string): Observable<ProfilPatient> {
    return this.http.get<ProfilPatient>(`/api/patients/${encodeURIComponent(patientId)}`);
  }

  modifierProfil(patientId: string, corps: ProfilModification): Observable<ProfilPatient> {
    return this.http.patch<ProfilPatient>(`/api/patients/${encodeURIComponent(patientId)}`, corps);
  }

  listerOrdonnances(patientId: string, statut?: StatutTraitement): Observable<Ordonnance[]> {
    return this.http.get<Ordonnance[]>(`/api/patients/${encodeURIComponent(patientId)}/ordonnances`, {
      params: parametres({ statut }),
    });
  }
}
