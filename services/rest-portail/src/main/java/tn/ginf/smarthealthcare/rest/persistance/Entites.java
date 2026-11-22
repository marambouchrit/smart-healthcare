package tn.ginf.smarthealthcare.rest.persistance;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Entités JPA du portail (P2.REST.7), une par table des migrations Flyway (V2 : ambulances). */
public final class Entites {

    private Entites() {}

    @Entity(name = "MedecinEntite")
    @Table(name = "medecin")
    public static class MedecinEntite {
        @Id public String id;
        public String nom;
        public String prenom;
        public String serviceCode;

        protected MedecinEntite() {}

        public MedecinEntite(String id, String nom, String prenom, String serviceCode) {
            this.id = id; this.nom = nom; this.prenom = prenom; this.serviceCode = serviceCode;
        }
    }

    @Entity(name = "PatientEntite")
    @Table(name = "patient")
    public static class PatientEntite {
        @Id public String id;
        public String nom;
        public String prenom;
        public LocalDate dateNaissance;
        public String sexe;
        public String telephone;
        public String email;
        public String rue;
        public String ville;
        public String codePostal;
        public String medecinTraitantId;

        protected PatientEntite() {}

        public PatientEntite(String id, String nom, String prenom, LocalDate dateNaissance, String sexe, String telephone,
                             String email, String rue, String ville, String codePostal, String medecinTraitantId) {
            this.id = id; this.nom = nom; this.prenom = prenom; this.dateNaissance = dateNaissance; this.sexe = sexe;
            this.telephone = telephone; this.email = email; this.rue = rue; this.ville = ville; this.codePostal = codePostal;
            this.medecinTraitantId = medecinTraitantId;
        }
    }

    @Entity(name = "TraitementEntite")
    @Table(name = "traitement")
    public static class TraitementEntite {
        @Id public String id;
        public String patientId;
        public String medecinId;
        public String medicament;
        public String dosage;
        public String posologie;
        public LocalDate dateDebut;
        public LocalDate dateFin;
        public String statut;

        protected TraitementEntite() {}

        public TraitementEntite(String id, String patientId, String medecinId, String medicament, String dosage,
                                String posologie, LocalDate dateDebut, LocalDate dateFin, String statut) {
            this.id = id; this.patientId = patientId; this.medecinId = medecinId; this.medicament = medicament;
            this.dosage = dosage; this.posologie = posologie; this.dateDebut = dateDebut; this.dateFin = dateFin;
            this.statut = statut;
        }
    }

    @Entity(name = "CreneauEntite")
    @Table(name = "creneau")
    public static class CreneauEntite {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String medecinId;
        public String serviceCode;
        public OffsetDateTime debut;
        public OffsetDateTime fin;

        protected CreneauEntite() {}

        public CreneauEntite(String medecinId, String serviceCode, OffsetDateTime debut, OffsetDateTime fin) {
            this.medecinId = medecinId; this.serviceCode = serviceCode; this.debut = debut; this.fin = fin;
        }
    }

    @Entity(name = "RendezVousEntite")
    @Table(name = "rendez_vous")
    public static class RendezVousEntite {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String patientId;
        public Long creneauId;
        public String motif;
        public String statut;
        public boolean urgence;
        public OffsetDateTime creeLe;

        protected RendezVousEntite() {}

        public RendezVousEntite(String patientId, Long creneauId, String motif, String statut, boolean urgence,
                                OffsetDateTime creeLe) {
            this.patientId = patientId; this.creneauId = creneauId; this.motif = motif; this.statut = statut;
            this.urgence = urgence; this.creeLe = creeLe;
        }
    }

    @Entity(name = "InterventionAmbulanceEntite")
    @Table(name = "intervention_ambulance")
    public static class InterventionAmbulanceEntite {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String patientId;
        public String identifiantAlerte;
        public String niveau;
        public String motif;
        public boolean aRegulariser;
        public String rue;
        public String ville;
        public String codePostal;
        public OffsetDateTime declencheeLe;
        public OffsetDateTime arriveeEstimee;

        protected InterventionAmbulanceEntite() {}

        public InterventionAmbulanceEntite(String patientId, String identifiantAlerte, String niveau, String motif,
                                           boolean aRegulariser, String rue, String ville, String codePostal,
                                           OffsetDateTime declencheeLe, OffsetDateTime arriveeEstimee) {
            this.patientId = patientId; this.identifiantAlerte = identifiantAlerte; this.niveau = niveau;
            this.motif = motif; this.aRegulariser = aRegulariser; this.rue = rue; this.ville = ville;
            this.codePostal = codePostal; this.declencheeLe = declencheeLe; this.arriveeEstimee = arriveeEstimee;
        }
    }
}
