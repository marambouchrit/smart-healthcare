package tn.ginf.smarthealthcare.soap.persistance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "traitement")
public class TraitementEntite {
    @Id
    private String id;
    private String patientId;
    private String medecinId;
    private String medicament;
    private String dosage;
    private LocalDate dateDebut;

    protected TraitementEntite() {}

    public TraitementEntite(String id, String patientId, String medecinId, String medicament, String dosage,
                            LocalDate dateDebut) {
        this.id = id;
        this.patientId = patientId;
        this.medecinId = medecinId;
        this.medicament = medicament;
        this.dosage = dosage;
        this.dateDebut = dateDebut;
    }

    public String getId() { return id; }
    public String getPatientId() { return patientId; }
    public String getMedecinId() { return medecinId; }
    public String getMedicament() { return medicament; }
    public String getDosage() { return dosage; }
    public LocalDate getDateDebut() { return dateDebut; }
}
