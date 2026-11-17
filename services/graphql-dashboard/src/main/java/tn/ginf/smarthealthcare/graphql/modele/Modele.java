package tn.ginf.smarthealthcare.graphql.modele;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Types du contrat GraphQL (les énumérations Java portent exactement les noms du schéma). */
public final class Modele {

    private Modele() {}

    public enum NiveauRisque { FAIBLE, MOYEN, ELEVE }
    public enum NiveauAlerte { INFO, URGENT, CRITIQUE }
    public enum TypeMesure { FREQUENCE_CARDIAQUE, SPO2, CHUTE }
    public enum StatutTraitement { EN_COURS, TERMINE }
    public enum StatutPriseEnCharge { PRISE_EN_CHARGE, A_REGULARISER }
    public enum ActionUrgence { CONSULTATION_URGENCE, AMBULANCE }

    public record ServiceHospitalier(String code, String libelle) {}

    public record Medecin(String id, String nom, String prenom, String specialite, String serviceCode) {}

    public record Patient(String id, String nom, String prenom, LocalDate dateNaissance, String sexe,
                          String telephone, String serviceCode, String pathologie, NiveauRisque niveauRisque,
                          String medecinTraitantId) {}

    public record Traitement(String id, String patientId, String medecinId, String medicament, String dosage,
                             String posologie, LocalDate dateDebut, LocalDate dateFin, StatutTraitement statut) {}

    public record Alerte(String id, String patientId, NiveauAlerte niveau, TypeMesure type, double valeur,
                         double seuil, String message, OffsetDateTime horodatage) {}

    public record FicheUrgence(String id, String patientId, String alerteId, StatutPriseEnCharge statutPriseEnCharge,
                               ActionUrgence action, String identifiantRendezVous, OffsetDateTime dateRendezVous,
                               String commentaire, OffsetDateTime creeeLe) {}

    // Entrées des mutations (input du schéma)
    public record AlerteInput(String identifiantAlerte, String identifiantPatient, NiveauAlerte niveau, TypeMesure type,
                              double valeur, double seuil, String message, OffsetDateTime horodatage) {}

    public record FicheUrgenceInput(String identifiantPatient, String identifiantAlerte,
                                    StatutPriseEnCharge statutPriseEnCharge, ActionUrgence action,
                                    String identifiantRendezVous, OffsetDateTime dateRendezVous, String commentaire) {}
}
