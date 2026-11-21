package tn.ginf.smarthealthcare.soap.persistance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "couverture")
public class CouvertureEntite {
    @Id
    @Column(name = "patient_id")
    private String patientId;
    private String codeVignette;
    private String filiere;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private int tauxRemboursement;

    protected CouvertureEntite() {}

    public CouvertureEntite(String patientId, String codeVignette, String filiere, LocalDate dateDebut,
                            LocalDate dateFin, int tauxRemboursement) {
        this.patientId = patientId;
        this.codeVignette = codeVignette;
        this.filiere = filiere;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.tauxRemboursement = tauxRemboursement;
    }

    public String getPatientId() { return patientId; }
    public String getCodeVignette() { return codeVignette; }
    public String getFiliere() { return filiere; }
    public LocalDate getDateDebut() { return dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public int getTauxRemboursement() { return tauxRemboursement; }
}
