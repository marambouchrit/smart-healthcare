import { LowerCasePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';

import { DossierSoapService, ErreurSoap, Historique, ReponseDroits } from '../../core/api/dossier-soap';
import { PatientResume, TableauDeBordService } from '../../core/api/tableau-de-bord';
import { dateHeure, dateLongue, jourTunis, messageErreur } from '../../core/format';
import { ReferentielService } from '../../core/referentiel';

const MOTIF_ID = /^TN-[0-9]{7}$/;

const STATUT: Record<ReponseDroits['statut'], string> = {
  OUVERTS: 'Droits ouverts',
  EXPIRES: 'Droits expirés',
  NON_AFFILIE: 'Non affilié',
};
const EVENEMENT: Record<string, string> = {
  CONSULTATION: 'Consultation',
  ACTE: 'Acte',
  HOSPITALISATION: 'Hospitalisation',
  URGENCE: 'Urgence',
};

interface Bloc<T> {
  resultat: T | null;
  erreur: string | null;
  requete: string | null;
  reponse: string | null;
}

const VIDE = { resultat: null, erreur: null, requete: null, reponse: null };

/**
 * Vue administrateur (P4.6) : opérations SOAP `verifierDroitsCNAM` et `consulterHistorique`
 * du service Dossier & CNAM, appelées via la gateway ; enveloppes SOAP visibles.
 */
@Component({
  selector: 'app-administration',
  imports: [FormsModule, LowerCasePipe],
  templateUrl: './administration.html',
})
export class Administration {
  private readonly soap = inject(DossierSoapService);
  protected readonly ref = inject(ReferentielService);

  protected readonly STATUT = STATUT;
  protected readonly EVENEMENT = EVENEMENT;
  protected readonly dateLongue = dateLongue;
  protected readonly dateHeure = dateHeure;

  /** Patients connus du tableau de bord (accessible à l'administrateur, D48) pour la liste de choix. */
  protected readonly patients = toSignal(
    inject(TableauDeBordService)
      .patients({})
      .pipe(catchError(() => of([] as PatientResume[]))),
    { initialValue: [] as PatientResume[] },
  );

  protected identifiant = 'TN-0012345';
  protected lettreCle = 'C';
  protected datePrestation = jourTunis(0);
  protected dateDebut = '';
  protected dateFin = '';

  protected readonly identifiantValide = signal(true);
  protected readonly droits = signal<Bloc<ReponseDroits>>(VIDE);
  protected readonly historique = signal<Bloc<Historique>>(VIDE);
  protected readonly enCours = signal<'droits' | 'historique' | null>(null);
  protected readonly lettres = computed(() => this.ref.lettresCles());

  protected verifierDroits(): void {
    if (!this.controler()) return;
    this.enCours.set('droits');
    this.soap.verifierDroitsCNAM(this.identifiant.trim(), this.lettreCle, this.datePrestation).subscribe({
      next: (e) => {
        this.enCours.set(null);
        this.droits.set({ resultat: e.resultat, erreur: null, requete: e.requete, reponse: e.reponse });
      },
      error: (e) => {
        this.enCours.set(null);
        this.droits.set(echec(e));
      },
    });
  }

  protected consulterHistorique(): void {
    if (!this.controler()) return;
    this.enCours.set('historique');
    this.soap.consulterHistorique(this.identifiant.trim(), this.dateDebut, this.dateFin).subscribe({
      next: (e) => {
        this.enCours.set(null);
        this.historique.set({ resultat: e.resultat, erreur: null, requete: e.requete, reponse: e.reponse });
      },
      error: (e) => {
        this.enCours.set(null);
        this.historique.set(echec(e));
      },
    });
  }

  protected libelleLettre(code: string): string {
    return this.lettres().find((l) => l.code === code)?.libelle ?? code;
  }

  private controler(): boolean {
    const ok = MOTIF_ID.test(this.identifiant.trim());
    this.identifiantValide.set(ok);
    return ok;
  }
}

function echec<T>(e: unknown): Bloc<T> {
  if (e instanceof ErreurSoap) {
    return { resultat: null, erreur: e.code ? `${e.code} : ${e.message}` : e.message, requete: e.requete, reponse: e.reponse };
  }
  return { resultat: null, erreur: messageErreur(e), requete: null, reponse: null };
}
