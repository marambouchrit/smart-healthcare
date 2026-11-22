import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

/*
 * Client du tableau de bord GraphQL (contrat : contracts/graphql/dashboard.graphqls), via la gateway.
 * Le client n'utilise que les requêtes (Query) ; les mutations sont réservées à l'orchestrateur (D5).
 */

export type NiveauRisque = 'FAIBLE' | 'MOYEN' | 'ELEVE';
export type NiveauAlerte = 'INFO' | 'URGENT' | 'CRITIQUE';
export type TypeMesure = 'FREQUENCE_CARDIAQUE' | 'SPO2' | 'CHUTE';
export type StatutCouverture = 'OUVERTS' | 'EXPIRES' | 'NON_AFFILIE';

export interface ServiceHospitalierGql {
  code: string;
  libelle: string;
}

export interface MedecinGql {
  id: string;
  nom: string;
  prenom: string;
  specialite: string;
}

export interface Constante {
  type: TypeMesure;
  valeur: number;
  horodatage: string;
}

export interface Alerte {
  id: string;
  niveau: NiveauAlerte;
  type: TypeMesure;
  valeur: number;
  seuil: number;
  message: string;
  horodatage: string;
}

export interface Couverture {
  statut: StatutCouverture;
  codeVignette: string | null;
  tauxRemboursement: number;
  dateFin: string | null;
  misAJourLe: string;
}

export interface TraitementGql {
  id: string;
  medicament: string;
  dosage: string;
  posologie: string;
  dateDebut: string;
  dateFin: string | null;
  prescripteur: MedecinGql;
}

export interface FicheUrgence {
  id: string;
  alerte: Pick<Alerte, 'id' | 'niveau' | 'message' | 'horodatage'>;
  statutPriseEnCharge: 'PRISE_EN_CHARGE' | 'A_REGULARISER';
  action: 'CONSULTATION_URGENCE' | 'AMBULANCE';
  identifiantRendezVous: string | null;
  dateRendezVous: string | null;
  commentaire: string | null;
  creeeLe: string;
}

export interface HistoriqueSoins {
  depuis: string | null;
  jusqua: string | null;
  nombreAlertes: number;
  alertesParNiveau: { niveau: NiveauAlerte; nombre: number }[];
  nombreFichesUrgence: number;
  nombreAmbulances: number;
  traitements: { id: string }[];
}

export interface PatientResume {
  id: string;
  nom: string;
  prenom: string;
  pathologie: string;
  niveauRisque: NiveauRisque;
  service: ServiceHospitalierGql;
  medecinTraitant: Pick<MedecinGql, 'id' | 'nom' | 'prenom'> | null;
  couverture: Pick<Couverture, 'statut'> | null;
  alertesRecentes: Pick<Alerte, 'id' | 'niveau' | 'message' | 'horodatage'>[];
}

export interface DossierPatient {
  id: string;
  nom: string;
  prenom: string;
  dateNaissance: string;
  sexe: string;
  telephone: string | null;
  pathologie: string;
  niveauRisque: NiveauRisque;
  service: ServiceHospitalierGql;
  medecinTraitant: MedecinGql | null;
  couverture: Couverture | null;
  dernieresConstantes: Constante[];
  alertesRecentes: Alerte[];
  traitementsEnCours: TraitementGql[];
  fichesUrgence: FicheUrgence[];
}

export interface FiltresPatients {
  serviceCode?: string;
  pathologie?: string;
  niveauRisque?: NiveauRisque[];
}

/** Erreur GraphQL (tableau `errors` de la réponse, D23). */
export class ErreurGraphql extends Error {
  constructor(readonly erreurs: { message: string; extensions?: { classification?: string } }[]) {
    super(erreurs.map((e) => e.message).join(' ; '));
  }
}

const PATIENTS = `
  query Patients($filtres: FiltresPatients) {
    patients(filtres: $filtres) {
      id nom prenom pathologie niveauRisque
      service { code libelle }
      medecinTraitant { id nom prenom }
      couverture { statut }
      alertesRecentes(limite: 1) { id niveau message horodatage }
    }
  }`;

const SERVICES = `query Services { services { code libelle } }`;

const DOSSIER = `
  query Dossier($id: ID!) {
    patient(id: $id) {
      id nom prenom dateNaissance sexe telephone pathologie niveauRisque
      service { code libelle }
      medecinTraitant { id nom prenom specialite }
      couverture { statut codeVignette tauxRemboursement dateFin misAJourLe }
      dernieresConstantes { type valeur horodatage }
      alertesRecentes(limite: 10) { id niveau type valeur seuil message horodatage }
      traitementsEnCours { id medicament dosage posologie dateDebut dateFin prescripteur { id nom prenom specialite } }
      fichesUrgence(limite: 10) {
        id statutPriseEnCharge action identifiantRendezVous dateRendezVous commentaire creeeLe
        alerte { id niveau message horodatage }
      }
    }
  }`;

const HISTORIQUE = `
  query Historique($id: ID!, $depuis: Date, $jusqua: Date) {
    patient(id: $id) {
      historiqueSoins(depuis: $depuis, jusqua: $jusqua) {
        depuis jusqua nombreAlertes nombreFichesUrgence nombreAmbulances
        alertesParNiveau { niveau nombre }
        traitements { id }
      }
    }
  }`;

@Injectable({ providedIn: 'root' })
export class TableauDeBordService {
  private readonly http = inject(HttpClient);

  services(): Observable<ServiceHospitalierGql[]> {
    return this.requete<{ services: ServiceHospitalierGql[] }>(SERVICES).pipe(map((d) => d.services));
  }

  patients(filtres: FiltresPatients): Observable<PatientResume[]> {
    return this.requete<{ patients: PatientResume[] }>(PATIENTS, { filtres }).pipe(map((d) => d.patients));
  }

  dossier(id: string): Observable<DossierPatient | null> {
    return this.requete<{ patient: DossierPatient | null }>(DOSSIER, { id }).pipe(map((d) => d.patient));
  }

  historique(id: string, depuis?: string, jusqua?: string): Observable<HistoriqueSoins | null> {
    return this.requete<{ patient: { historiqueSoins: HistoriqueSoins } | null }>(HISTORIQUE, {
      id,
      depuis: depuis || null,
      jusqua: jusqua || null,
    }).pipe(map((d) => d.patient?.historiqueSoins ?? null));
  }

  private requete<T>(query: string, variables?: Record<string, unknown>): Observable<T> {
    return this.http
      .post<{ data?: T; errors?: ErreurGraphql['erreurs'] }>('/graphql', { query, variables })
      .pipe(
        map((reponse) => {
          if (reponse.errors?.length) throw new ErreurGraphql(reponse.errors);
          return reponse.data as T;
        }),
      );
  }
}
