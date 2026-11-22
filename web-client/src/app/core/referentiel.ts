import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';

/*
 * Noms des médecins, des services et des lettres-clés CNAM, lus dans les fichiers de contracts/seed/ copiés tels quels
 * dans le build par copier-seed.mjs (servis sous /seed/). Aucune donnée n'est saisie dans le client (D49).
 */

export interface Medecin {
  id: string;
  nom: string;
  prenom: string;
  specialite: string;
  serviceCode: string;
}

export interface LettreCle {
  code: string;
  libelle: string;
}

export interface ServiceHospitalier {
  code: string;
  libelle: string;
}

@Injectable({ providedIn: 'root' })
export class ReferentielService {
  private readonly http = inject(HttpClient);

  readonly medecins = toSignal(
    this.http.get<Medecin[]>('seed/medecins.json').pipe(catchError(() => of([] as Medecin[]))),
    { initialValue: [] as Medecin[] },
  );
  readonly services = toSignal(
    this.http.get<ServiceHospitalier[]>('seed/services-hospitaliers.json').pipe(
      catchError(() => of([] as ServiceHospitalier[])),
    ),
    { initialValue: [] as ServiceHospitalier[] },
  );

  readonly lettresCles = toSignal(
    this.http.get<LettreCle[]>('seed/lettres-cles.json').pipe(catchError(() => of([] as LettreCle[]))),
    { initialValue: [] as LettreCle[] },
  );

  private readonly medecinsParId = computed(() => new Map(this.medecins().map((m) => [m.id, m])));
  private readonly servicesParCode = computed(() => new Map(this.services().map((s) => [s.code, s])));

  nomMedecin(id: string | null | undefined): string {
    if (!id) return '—';
    const m = this.medecinsParId().get(id);
    return m ? `Dr ${m.prenom} ${m.nom}` : id;
  }

  libelleService(code: string | null | undefined): string {
    if (!code) return '—';
    return this.servicesParCode().get(code)?.libelle ?? code;
  }
}
