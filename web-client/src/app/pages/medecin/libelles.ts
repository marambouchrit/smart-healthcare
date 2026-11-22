import { NiveauAlerte, NiveauRisque, StatutCouverture, TypeMesure } from '../../core/api/tableau-de-bord';

export const RISQUE: Record<NiveauRisque, string> = { FAIBLE: 'Faible', MOYEN: 'Moyen', ELEVE: 'Élevé' };
export const NIVEAU: Record<NiveauAlerte, string> = { INFO: 'Info', URGENT: 'Urgent', CRITIQUE: 'Critique' };
export const MESURE: Record<TypeMesure, string> = {
  FREQUENCE_CARDIAQUE: 'Fréquence cardiaque',
  SPO2: 'SpO₂',
  CHUTE: 'Chute',
};
export const COUVERTURE: Record<StatutCouverture, string> = {
  OUVERTS: 'Droits ouverts',
  EXPIRES: 'Droits expirés',
  NON_AFFILIE: 'Non affilié',
};

/** Valeur d'une constante avec son unité (unités de telemetrie.proto). */
export function valeurMesure(type: TypeMesure, valeur: number): string {
  if (type === 'FREQUENCE_CARDIAQUE') return `${Math.round(valeur)} bpm`;
  if (type === 'SPO2') return `${Math.round(valeur)} %`;
  return valeur >= 1 ? 'Chute détectée' : 'Aucune chute';
}
