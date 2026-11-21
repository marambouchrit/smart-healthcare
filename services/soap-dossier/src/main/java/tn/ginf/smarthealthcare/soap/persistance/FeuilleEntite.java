package tn.ginf.smarthealthcare.soap.persistance;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "feuille_de_soins")
public class FeuilleEntite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String numero;
    private String patientId;
    private String codeVignette;
    private LocalDate dateEmission;
    private BigDecimal montantTotal;
    private int tauxRemboursement;
    private BigDecimal montantRembourse;
    private String codeCertification;

    @OneToMany(mappedBy = "feuille", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre")
    private List<ActeEntite> actes = new ArrayList<>();

    protected FeuilleEntite() {}

    public FeuilleEntite(String patientId, String codeVignette, LocalDate dateEmission, BigDecimal montantTotal,
                         int tauxRemboursement, BigDecimal montantRembourse) {
        this.patientId = patientId;
        this.codeVignette = codeVignette;
        this.dateEmission = dateEmission;
        this.montantTotal = montantTotal;
        this.tauxRemboursement = tauxRemboursement;
        this.montantRembourse = montantRembourse;
    }

    public void ajouter(ActeEntite acte) {
        acte.rattacher(this, actes.size() + 1);
        actes.add(acte);
    }

    /** Le numéro dépend de l'identifiant attribué par la base : il est posé juste après l'insertion. */
    public void numeroter(String numero, String codeCertification) {
        this.numero = numero;
        this.codeCertification = codeCertification;
    }

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public String getPatientId() { return patientId; }
    public String getCodeVignette() { return codeVignette; }
    public LocalDate getDateEmission() { return dateEmission; }
    public BigDecimal getMontantTotal() { return montantTotal; }
    public int getTauxRemboursement() { return tauxRemboursement; }
    public BigDecimal getMontantRembourse() { return montantRembourse; }
    public String getCodeCertification() { return codeCertification; }
    public List<ActeEntite> getActes() { return actes; }
}
